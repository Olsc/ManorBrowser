/**
 * 浏览器内置下载器
 * 使用 OkHttp 实现手动下载逻辑，支持通知栏进度显示、多线程下载以及 Firefox WebExtension (.xpi) 的自动安装。
 */
package com.olsc.manorbrowser.utils;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.activity.MainActivity;
import com.olsc.manorbrowser.data.DownloadStorage;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BrowserDownloader {
    /** 下载通知渠道 ID */
    private static final String CHANNEL_ID = "browser_downloads_http";
    /** 单例 OkHttpClient 实例 */
    private static OkHttpClient client;

    /**
     * 获取 OkHttpClient 单例，针对局域网环境进行深度优化
     */
    private static synchronized OkHttpClient getClient() {
        if (client == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true);

            // 1. 实现智能代理选择：局域网地址不走代理
            builder.proxySelector(new java.net.ProxySelector() {
                @Override
                public java.util.List<java.net.Proxy> select(java.net.URI uri) {
                    String host = uri.getHost();
                    if (host != null && (host.equals("localhost") || host.equals("127.0.0.1") || 
                        host.startsWith("192.168.") || host.startsWith("10.") || 
                        host.startsWith("172.16.") || host.startsWith("172.17.") || 
                        host.startsWith("172.18.") || host.startsWith("172.19.") || 
                        host.startsWith("172.2") || host.startsWith("172.3"))) {
                        return java.util.Collections.singletonList(java.net.Proxy.NO_PROXY);
                    }
                    return java.net.ProxySelector.getDefault().select(uri);
                }

                @Override
                public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                    java.net.ProxySelector.getDefault().connectFailed(uri, sa, ioe);
                }
            });

            // 2. 增强 SSL 兼容性（局域网很多是自签名证书）
            try {
                @SuppressLint("CustomX509TrustManager") final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                    }
                };
                final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 3. 局域网服务器往往不支持 HTTP/2，强制使用 HTTP/1.1 提高兼容性
            builder.protocols(List.of(Protocol.HTTP_1_1));

            client = builder.build();
        }
        return client;
    }

    /** 正在进行的下载任务映射表，用于支持取消功能 */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Call> activeCalls = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 取消特定的下载任务
     * @param taskId 任务 ID (时间戳)
     */
    public static void cancel(long taskId) {
        Call call = activeCalls.remove(taskId);
        if (call != null) {
            try {
                call.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 核心下载方法（流式版本）——直接使用 GeckoView 已经建立的连接流写入磁盘。
     *
     * <p>这是下载需要认证（Cookie/Session）的资源时的正确方式。
     * GeckoView 在调用 onExternalResponse 时已经用自己的网络栈（含完整 Cookie）
     * 建立了连接，只需把这个已认证的流直接写到磁盘，无需 OkHttp 重新发起请求。</p>
     *
     * @param context   上下文
     * @param url       下载地址（仅用于日志和记录）
     * @param mimeType  MIME 类型
     * @param filename  保存的文件名
     * @param totalLength 文件总大小（从 Content-Length header 读取，-1 表示未知）
     * @param inputStream GeckoView 提供的已认证响应体流
     */
    public static void downloadFromStream(Context context, String url, String mimeType, String filename,
                                          long totalLength, java.io.InputStream inputStream) {
        if (inputStream == null) return;
        
        Context appContext = context.getApplicationContext();

        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(appContext, R.string.msg_download_starting, Toast.LENGTH_SHORT).show()
        );

        NotificationManager notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                appContext.getString(R.string.action_downloads), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        final long taskId = System.currentTimeMillis();
        final com.olsc.manorbrowser.data.DownloadInfo downloadInfo =
                new com.olsc.manorbrowser.data.DownloadInfo(taskId, filename, url, null, null);
        downloadInfo.status = 1;
        downloadInfo.isInternal = true;
        downloadInfo.totalBytes = totalLength;
        DownloadHelper.sInternalDownloads.add(downloadInfo);
        DownloadStorage.saveDownload(appContext, downloadInfo);

        final int notificationId = (int) taskId;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(filename)
                .setContentText(appContext.getString(R.string.msg_download_starting))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        notificationManager.notify(notificationId, builder.build());

        // 在后台线程中把流写到磁盘
        final java.io.InputStream finalStream = inputStream;
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            java.io.OutputStream os = null;
            android.net.Uri fileUri = null;
            File legacyFile = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                    if (!TextUtils.isEmpty(mimeType)) values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    fileUri = appContext.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (fileUri != null) {
                        os = appContext.getContentResolver().openOutputStream(fileUri);
                        downloadInfo.filePath = fileUri.toString();
                    }
                }
                if (os == null) {
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) downloadDir.mkdirs();
                    legacyFile = new File(downloadDir, filename);
                    int counter = 1;
                    if (legacyFile.exists()) {
                        String baseName = filename, ext = "";
                        int dot = filename.lastIndexOf('.');
                        if (dot > 0) { baseName = filename.substring(0, dot); ext = filename.substring(dot); }
                        while (legacyFile.exists()) legacyFile = new File(downloadDir, baseName + " (" + counter++ + ")" + ext);
                    }
                    os = new FileOutputStream(legacyFile);
                    downloadInfo.filePath = legacyFile.getAbsolutePath();
                }

                byte[] buffer = new byte[32768];
                int read;
                long downloaded = 0;
                long lastUpdate = 0, lastStore = 0;
                while ((read = finalStream.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                    downloaded += read;
                    downloadInfo.currentBytes = downloaded;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 500) {
                        lastUpdate = now;
                        if (totalLength > 0) {
                            int pct = (int) ((downloaded * 100) / totalLength);
                            builder.setProgress(100, pct, false)
                                   .setContentText(String.format(appContext.getString(R.string.download_status_running), pct));
                        } else {
                            builder.setProgress(0, 0, true)
                                   .setContentText(appContext.getString(R.string.download_status_waiting) + " " + (downloaded / 1024) + " KB");
                        }
                        notificationManager.notify(notificationId, builder.build());
                    }
                    if (now - lastStore > 2000) {
                        lastStore = now;
                        DownloadStorage.saveDownload(appContext, downloadInfo);
                    }
                }
                os.flush();

                downloadInfo.status = 3;
                DownloadStorage.saveDownload(appContext, downloadInfo);
                builder.setContentText(appContext.getString(R.string.download_status_completed))
                       .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true);
                notificationManager.notify(notificationId, builder.build());
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(appContext, appContext.getString(R.string.download_status_completed), Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                Log.e("BrowserDownloader", "Stream download error", e);
                downloadInfo.status = 4;
                downloadInfo.reason = 1001;
                DownloadStorage.saveDownload(appContext, downloadInfo);
                builder.setContentText(appContext.getString(R.string.download_status_failed))
                       .setProgress(0, 0, false).setOngoing(false);
                notificationManager.notify(notificationId, builder.build());
                if (fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { appContext.getContentResolver().delete(fileUri, null, null); } catch (Exception ignored) {}
                }
            } finally {
                try { if (os != null) os.close(); } catch (Exception ignored) {}
                try { finalStream.close(); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 执行下载的核心方法（OkHttp 版本，用于非 GeckoView 触发的下载）
     *
     * @param context   上下文
     * @param url       下载地址
     * @param userAgent User-Agent
     * @param cookie    Cookie
     * @param referer   Referer
     * @param filename  保存的文件名
     */
    public static void download(Context context, String url, String userAgent, String cookie, String referer, String filename) {
        Context appContext = context.getApplicationContext();
        
        // 提早弹出 Toast 通知下载已开始
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(appContext, R.string.msg_download_starting, Toast.LENGTH_SHORT).show()
        );

        NotificationManager notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建通知渠道 (Android 8.0+)
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.action_downloads),
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);

        // 初始化任务追踪对象，供下载管理界面使用
        final long taskId = System.currentTimeMillis();
        final com.olsc.manorbrowser.data.DownloadInfo downloadInfo = new com.olsc.manorbrowser.data.DownloadInfo(
            taskId, filename, url, null, null);
        downloadInfo.status = 1; // 正在下载
        downloadInfo.isInternal = true;
        DownloadHelper.sInternalDownloads.add(downloadInfo);
        DownloadStorage.saveDownload(appContext, downloadInfo);

        // 初始化通知
        final int notificationId = (int) taskId;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(filename)
                .setContentText(appContext.getString(R.string.msg_download_starting))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        notificationManager.notify(notificationId, builder.build());

        // 构造 OkHttp 请求
        Request.Builder reqBuilder = new Request.Builder().url(url);
        // 赋予更像浏览器的默认 Header
        reqBuilder.header("Accept", "*/*");
        reqBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        
        if (!TextUtils.isEmpty(userAgent)) reqBuilder.header("User-Agent", userAgent);
        else reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36");
        
        if (!TextUtils.isEmpty(cookie)) reqBuilder.header("Cookie", cookie);
        if (!TextUtils.isEmpty(referer) && !referer.startsWith("about:")) reqBuilder.header("Referer", referer);

        // 创建请求并入队追踪
        Call call = getClient().newCall(reqBuilder.build());
        activeCalls.put(taskId, call);

        // 开始异步网络请求
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                activeCalls.remove(taskId);
                if (call.isCanceled()) return; // 若是手动取消则不报错

                Log.e("BrowserDownloader", "Download failed: " + e.getMessage());
                // 下载失败回调
                downloadInfo.status = 4; // 失败
                downloadInfo.reason = 1000; // 自定义错误起始码
                DownloadStorage.saveDownload(appContext, downloadInfo);
                builder.setContentText(appContext.getString(R.string.download_status_failed))
                       .setProgress(0, 0, false)
                       .setOngoing(false);
                notificationManager.notify(notificationId, builder.build());
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(appContext, appContext.getString(R.string.msg_download_failed, filename), Toast.LENGTH_SHORT).show());
            }

            @SuppressLint("Recycle")
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                activeCalls.remove(taskId);
                if (call.isCanceled()) {
                    response.close();
                    return;
                }

                if (!response.isSuccessful()) {
                    downloadInfo.status = 4;
                    downloadInfo.reason = response.code(); // 记录 HTTP 错误码
                    DownloadStorage.saveDownload(appContext, downloadInfo);
                    builder.setContentText(appContext.getString(R.string.download_status_failed))
                           .setProgress(0, 0, false)
                           .setOngoing(false);
                    notificationManager.notify(notificationId, builder.build());
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(appContext, appContext.getString(R.string.msg_download_failed, filename), Toast.LENGTH_SHORT).show());
                    response.close();
                    return;
                }

                String mimeType = response.header("Content-Type");
                downloadInfo.mimeType = mimeType;

                // --- 处理 Android 11+ (Scoped Storage) 文件写入 ---
                java.io.OutputStream os = null;
                android.net.Uri fileUri = null;
                File legacyFile = null;

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            // 使用 MediaStore 写入 Downloads 目录，兼容 Android 10-16
                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                            if (!TextUtils.isEmpty(mimeType)) values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                            fileUri = appContext.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (fileUri != null) {
                                os = appContext.getContentResolver().openOutputStream(fileUri);
                                downloadInfo.filePath = fileUri.toString();
                            }
                        } catch (Exception medE) {
                            Log.e("BrowserDownloader", "MediaStore insert failed", medE);
                        }
                    } 
                    
                    // Fallback 或 低版本 Android
                    if (os == null) {
                        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!downloadDir.exists()) downloadDir.mkdirs();
                        
                        legacyFile = new File(downloadDir, filename);
                        if (legacyFile.exists()) {
                            String baseName = filename;
                            String extension = "";
                            int dot = filename.lastIndexOf('.');
                            if (dot > 0) {
                                baseName = filename.substring(0, dot);
                                extension = filename.substring(dot);
                            }
                            int counter = 1;
                            while (legacyFile.exists()) {
                                legacyFile = new File(downloadDir, baseName + " (" + counter + ")" + extension);
                                counter++;
                            }
                        }
                        os = new FileOutputStream(legacyFile);
                        downloadInfo.filePath = legacyFile.getAbsolutePath();
                    }

                    try (InputStream is = response.body().byteStream();
                         java.io.OutputStream fos = os) {

                        long totalLength = response.body().contentLength();
                        downloadInfo.totalBytes = totalLength;
                        long downloaded = 0;
                        byte[] buffer = new byte[32768]; // 增大缓冲区
                        int read;
                        long lastUpdateTime = 0;
                        long lastStorageTime = 0;

                        while ((read = is.read(buffer)) != -1) {
                            if (call.isCanceled()) throw new java.io.IOException("Canceled");
                            
                            fos.write(buffer, 0, read);
                            downloaded += read;
                            downloadInfo.currentBytes = downloaded;

                            long currentTime = System.currentTimeMillis();
                            // 每 500ms 更新一次 UI (仅在未取消时)
                            if (currentTime - lastUpdateTime > 500) {
                                lastUpdateTime = currentTime;
                                if (call.isCanceled()) break;
                                
                                if (totalLength > 0) {
                                    int progress = (int) ((downloaded * 100) / totalLength);
                                    builder.setProgress(100, progress, false);
                                    builder.setContentText(String.format(appContext.getString(R.string.download_status_running), progress));
                                } else {
                                    builder.setProgress(0, 0, true);
                                    builder.setContentText(appContext.getString(R.string.download_status_waiting) + " " + (downloaded / 1024) + " KB");
                                }
                                notificationManager.notify(notificationId, builder.build());
                            }
                            
                            // 每 2秒 同步一次存储 (仅在未取消时)
                            if (currentTime - lastStorageTime > 2000) {
                                lastStorageTime = currentTime;
                                if (!call.isCanceled()) {
                                    DownloadStorage.saveDownload(appContext, downloadInfo);
                                }
                            }
                        }
                        fos.flush();

                        if (call.isCanceled()) throw new java.io.IOException("Canceled");

                        // 下载完成
                        downloadInfo.status = 3; // 成功
                        DownloadStorage.saveDownload(appContext, downloadInfo);
                        
                        builder.setContentText(appContext.getString(R.string.download_status_completed))
                               .setProgress(0, 0, false)
                               .setOngoing(false);

                        // 特殊逻辑：处理 Firefox 扩展文件 (.xpi)
                        if (filename.toLowerCase().endsWith(".xpi") && legacyFile != null) {
                            handleExtensionInstall(appContext, legacyFile, filename);
                        }

                        builder.setAutoCancel(true);
                        notificationManager.notify(notificationId, builder.build());
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(appContext, appContext.getString(R.string.download_status_completed), Toast.LENGTH_SHORT).show());

                    }
                } catch (Exception e) {
                    if (call.isCanceled() || "Canceled".equals(e.getMessage())) {
                        Log.d("BrowserDownloader", "Download canceled silently and cleaned");
                        // 确保彻底从活跃列表中移除
                        DownloadHelper.sInternalDownloads.removeIf(i -> i.id == taskId);
                    } else {
                        Log.e("BrowserDownloader", "Download error", e);
                        downloadInfo.status = 4;
                        downloadInfo.reason = 1001; // 写文件异常码
                        DownloadStorage.saveDownload(appContext, downloadInfo);
                        builder.setContentText(appContext.getString(R.string.download_status_failed))
                               .setProgress(0, 0, false)
                               .setOngoing(false);
                        notificationManager.notify(notificationId, builder.build());
                        if (fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try { appContext.getContentResolver().delete(fileUri, null, null); } catch (Exception ignored) {}
                        }
                    }
                } finally {
                    response.close();
                    activeCalls.remove(taskId);
                }
            }
        });
    }

    /**
     * 处理下载完成后的 Firefox 扩展安装
     */
    private static void handleExtensionInstall(Context appContext, File outputFile, String filename) {
        File tempFile = getFile(appContext, outputFile, filename);

        // 调用 GeckoView 运行时的扩展控制器进行安装
        if (MainActivity.sRuntime != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (MainActivity.sRuntime != null) {
                    MainActivity.sRuntime.getWebExtensionController().install("file://" + tempFile.getAbsolutePath())
                            .accept(
                                    ext -> {
                                        new Handler(Looper.getMainLooper()).post(() ->
                                                Toast.makeText(appContext, appContext.getString(R.string.msg_extension_installed, ext.metaData.name), Toast.LENGTH_LONG).show()
                                        );
                                    },
                                    e -> {
                                        new Handler(Looper.getMainLooper()).post(() ->
                                                Toast.makeText(appContext, appContext.getString(R.string.msg_extension_install_failed), Toast.LENGTH_SHORT).show()
                                        );
                                    }
                            );
                }
            });
        }
    }

    @SuppressLint("SetWorldReadable")
    @NonNull
    private static File getFile(Context appContext, File outputFile, String filename) {
        File tempDir = new File(appContext.getCacheDir(), "extensions");
        if (!tempDir.exists()) tempDir.mkdirs();
        tempDir.setExecutable(true, false);
        tempDir.setReadable(true, false);
        File tempFile = new File(tempDir, filename);

        try (InputStream in = new java.io.FileInputStream(outputFile);
             FileOutputStream out = new FileOutputStream(tempFile)) {
            // 将文件拷贝到内部缓存，以解决权限问题
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            tempFile.setReadable(true, false);
        } catch (Exception ignored) { }
        return tempFile;
    }
}
