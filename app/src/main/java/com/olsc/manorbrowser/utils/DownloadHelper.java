/**
 * 下载辅助类
 * 负责下载任务的调度、管理系统下载器与内置下载器、文件查重以及下载历史查询。
 */
package com.olsc.manorbrowser.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.DownloadInfo;

import java.util.ArrayList;
import java.util.List;

public class DownloadHelper {
    private static final String TAG = "DownloadHelper";
    
    /** 存储内置下载器正在进行的任务，用于在下载列表中实时展示 */
    public static final List<DownloadInfo> sInternalDownloads = java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * 开始下载任务
     * 根据设置分发给系统下载器或浏览器内置下载器。
     */
    public static void startDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType, String cookie, String referer) {
        if (TextUtils.isEmpty(url)) return;

        // 获取用户偏好：是否使用系统下载器（默认设为 false，推荐使用内置下载器）
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSystemDownloader = prefs.getBoolean(com.olsc.manorbrowser.Config.PREF_KEY_USE_SYSTEM_DOWNLOADER, false);

        if (!useSystemDownloader) {
            // 使用浏览器内置下载器
            startBrowserDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer);
        } else {
            // 使用系统内置下载管理器 (DownloadManager)
            startSystemDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer);
        }
    }

    /**
     * 调用浏览器内置下载器逻辑
     */
    private static void startBrowserDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType, String cookie, String referer) {
        String filename = guessFileName(url, contentDisposition, mimeType);
        // 内置下载器直接开始，不弹出重复警告，由其内部处理同名覆盖/重命名
        BrowserDownloader.download(context, url, userAgent, cookie, referer, filename);
    }

    /**
     * 使用 Android 系统下载器开始下载
     */
    private static void startSystemDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType, String cookie, String referer) {
        String filename = guessFileName(url, contentDisposition, mimeType);
        performDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer, filename);
    }

    /**
     * 检查文件是否存在并执行下载逻辑
     */
    private static void checkExistingFileAndDownload(Context context, String url, String originalFilename,
                                                     String userAgent, String contentDisposition, String mimeType,
                                                     String cookie, String referer) {
        new Thread(() -> {
            try {
                java.io.File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File existingFile = new java.io.File(downloadDir, originalFilename);

                if (existingFile.exists()) {
                    // 文件已存在，主线程弹出 Material 对话框询问
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.title_file_exists)
                                .setMessage(context.getString(R.string.msg_file_exists, originalFilename))
                                .setPositiveButton(R.string.action_download_new, (dialog, which) -> {
                                    String newFilename = getUniqueFilename(downloadDir, originalFilename);
                                    performDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer, newFilename);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    });
                } else {
                    performDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer, originalFilename);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking existing file", e);
                // 出错时直接下载
                performDownload(context, url, userAgent, contentDisposition, mimeType, cookie, referer, originalFilename);
            }
        }).start();
    }

    /**
     * 生成唯一文件名（若重名则添加序号）
     */
    private static String getUniqueFilename(java.io.File dir, String filename) {
        // 分离文件名和扩展名
        String name = filename;
        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            name = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        }

        // 查找可用的序号
        int counter = 1;
        String newFilename = filename;
        while (new java.io.File(dir, newFilename).exists()) {
            newFilename = name + " (" + counter + ")" + extension;
            counter++;
        }
        return newFilename;
    }

    /**
     * 执行实际的系统下载入队操作
     */
    private static void performDownload(Context context, String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        String cookie, String referer, String filename) {
        // 主线程提示已开始下载
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.msg_download_starting, Toast.LENGTH_SHORT).show()
        );

        final Context appContext = context.getApplicationContext();

        new Thread(() -> {
            try {
                DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request;
                try {
                    request = new DownloadManager.Request(Uri.parse(url));
                } catch (Exception e) {
                    Log.e(TAG, "Invalid URL: " + url);
                    return;
                }

                // 系统下载器会自动处理同名文件（通常是添加后缀），我们手动添加一个时间戳确保唯一性，避免覆盖
                String extension = "";
                String baseName = filename;
                int dot = filename.lastIndexOf('.');
                if (dot > 0) {
                    baseName = filename.substring(0, dot);
                    extension = filename.substring(dot);
                }
                String finalFilename = baseName + "_" + (System.currentTimeMillis() % 100000) + extension;

                // 配置请求头
                if (!TextUtils.isEmpty(userAgent)) {
                    request.addRequestHeader("User-Agent", sanitize(userAgent));
                }
                if (!TextUtils.isEmpty(cookie) && cookie.length() > 2) {
                    request.addRequestHeader("Cookie", sanitize(cookie));
                }
                if (!TextUtils.isEmpty(referer) && !referer.startsWith("about:")) {
                    request.addRequestHeader("Referer", sanitize(referer));
                }

                // 下载通知配置
                request.setTitle(filename);
                request.setDescription(url);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
                request.allowScanningByMediaScanner();

                try {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFilename);
                } catch (Exception e) {
                    // 若公共目录不可写，尝试私有目录
                    request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, finalFilename);
                }

                long id = dm.enqueue(request);
                Log.d(TAG, "Download enqueued with ID: " + id);
                
                // 将系统任务也存入本地持久化历史，增加双重保险
                com.olsc.manorbrowser.data.DownloadInfo sysInfo = new com.olsc.manorbrowser.data.DownloadInfo(
                    id, filename, url, null, mimeType);
                sysInfo.status = 1; // 正在运行
                sysInfo.isInternal = false;
                com.olsc.manorbrowser.data.DownloadStorage.saveDownload(appContext, sysInfo);

                dm.query(new DownloadManager.Query().setFilterById(id));
                cleanOldTasks(appContext, url);
            } catch (Exception e) {
                Log.e(TAG, "Enqueue failed", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, R.string.msg_download_start_failed, Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    /**
     * 清理 HTTP Header 中的换行符
     */
    private static String sanitize(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n]", "").trim();
    }

    /**
     * 清理同一 URL 之下已失败的旧任务（避免列表堆积无效任务）
     */
    private static void cleanOldTasks(Context context, String url) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        // 仅清理失败任务，不要清理暂停任务（暂停可能是因为系统流量保护等原因）
        query.setFilterByStatus(DownloadManager.STATUS_FAILED);
        try (Cursor cursor = dm.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int urlIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                int idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                do {
                    String rowUrl = cursor.getString(urlIdx);
                    if (url.equals(rowUrl)) {
                        dm.remove(cursor.getLong(idIdx));
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {}
    }

    /**
     * 通过 URL、Content-Disposition 和 MimeType 预估文件名
     */
    public static String guessFileName(String url, String contentDisposition, String mimeType) {
        String decodedUrl = android.net.Uri.decode(url);
        String filename = URLUtil.guessFileName(decodedUrl, contentDisposition, mimeType);
        if (filename != null) {
            filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        if (TextUtils.isEmpty(filename) || filename.endsWith(".bin")) {
            String path = Uri.parse(url).getLastPathSegment();
            if (!TextUtils.isEmpty(path)) filename = path;
            else filename = "download_" + System.currentTimeMillis();
        }
        return filename;
    }

    /**
     * 获取系统下载管理器中的所有下载记录
     */
    public static List<DownloadInfo> getDownloads(Context context) {
        // 使用 Map 进行去重，Key 为下载 ID
        java.util.Map<Long, DownloadInfo> allInfoMap = new java.util.HashMap<>();

        // 1. 加载已持久化的历史记录（包含内置和部分已持久化的系统记录）
        List<DownloadInfo> stored = com.olsc.manorbrowser.data.DownloadStorage.getAllDownloads(context);
        for (DownloadInfo info : stored) {
            allInfoMap.put(info.id, info);
        }

        // 2. 覆盖/补充内存中正活跃的内置任务（进度最实时）
        synchronized (sInternalDownloads) {
            for (DownloadInfo info : sInternalDownloads) {
                allInfoMap.put(info.id, info);
            }
        }

        // 3. 补充/覆盖系统下载管理器的任务（获取系统任务的最新状态）
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        try (Cursor cursor = dm.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int currentIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int mimeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                int dateIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
                int localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                do {
                    long id = cursor.getLong(idIdx);
                    String title = cursor.getString(titleIdx);
                    String uri = cursor.getString(uriIdx);
                    int status = cursor.getInt(statusIdx);
                    long total = cursor.getLong(totalIdx);
                    long current = cursor.getLong(currentIdx);
                    String mime = cursor.getString(mimeIdx);
                    long date = cursor.getLong(dateIdx);
                    String localUri = cursor.getString(localUriIdx);
                    int reason = cursor.getInt(reasonIdx);

                    DownloadInfo info = new DownloadInfo(id, title, uri, localUri, mime);
                    info.totalBytes = total;
                    info.currentBytes = current;
                    info.timestamp = date;
                    info.reason = reason;
                    info.isInternal = false;

                    switch (status) {
                        case DownloadManager.STATUS_PENDING: info.status = 0; break;
                        case DownloadManager.STATUS_RUNNING: info.status = 1; break;
                        case DownloadManager.STATUS_PAUSED:  info.status = 2; break;
                        case DownloadManager.STATUS_SUCCESSFUL: info.status = 3; break;
                        case DownloadManager.STATUS_FAILED:  info.status = 4; break;
                    }
                    allInfoMap.put(id, info);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<DownloadInfo> list = new ArrayList<>(allInfoMap.values());
        // 按时间倒序排列
        list.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));
        return list;
    }
}
