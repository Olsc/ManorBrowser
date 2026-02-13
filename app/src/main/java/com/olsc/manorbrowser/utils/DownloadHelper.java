package com.olsc.manorbrowser.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Toast;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.DownloadInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadHelper {
    private static final String TAG = "DownloadHelper";

    public static long startDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType, String cookie, String referer) {
        if (TextUtils.isEmpty(url)) return -1;

        final String finalUrl = url.trim();
        final Context appContext = context.getApplicationContext();
        
        Toast.makeText(appContext, R.string.msg_download_starting, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request;
                try {
                    request = new DownloadManager.Request(Uri.parse(finalUrl));
                } catch (Exception e) {
                    Log.e(TAG, "Invalid URL: " + finalUrl);
                    return;
                }

                String originalFilename = guessFileName(finalUrl, contentDisposition, mimeType);
                String filename = System.currentTimeMillis() + "_" + originalFilename;

                if (!TextUtils.isEmpty(userAgent)) {
                    request.addRequestHeader("User-Agent", sanitize(userAgent));
                }
                
                if (!TextUtils.isEmpty(cookie) && cookie.length() > 2) {
                    request.addRequestHeader("Cookie", sanitize(cookie));
                }
                
                if (!TextUtils.isEmpty(referer) && !referer.startsWith("about:")) {
                    request.addRequestHeader("Referer", sanitize(referer));
                }

                request.setTitle(originalFilename);
                request.setDescription(finalUrl);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
                
                request.allowScanningByMediaScanner();

                try {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                } catch (Exception e) {
                    request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, filename);
                }

                long id = dm.enqueue(request);
                Log.d(TAG, "Download enqueued with ID: " + id);
                
                dm.query(new DownloadManager.Query().setFilterById(id));
                
                cleanOldTasks(appContext, finalUrl);
            } catch (Exception e) {
                Log.e(TAG, "Enqueue failed", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    Toast.makeText(appContext, R.string.msg_download_start_failed, Toast.LENGTH_SHORT).show()
                );
            }
        }).start();

        return 0; // 由于异步，返回值不再可靠，但在本项目中调用方不使用该返回值
    }

    private static String sanitize(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n]", "").trim();
    }

    private static void cleanOldTasks(Context context, String url) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(DownloadManager.STATUS_FAILED | DownloadManager.STATUS_PAUSED);
        
        try (Cursor cursor = dm.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int urlIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                int idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                do {
                    String rowUrl = cursor.getString(urlIdx);
                    int status = cursor.getInt(statusIdx);
                    if (url.equals(rowUrl) && (status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_PAUSED)) {
                        dm.remove(cursor.getLong(idIdx));
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {}
    }

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

    public static List<DownloadInfo> getDownloads(Context context) {
        List<DownloadInfo> list = new ArrayList<>();
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
                     
                     switch (status) {
                         case DownloadManager.STATUS_PENDING: info.status = 0; break;
                         case DownloadManager.STATUS_RUNNING: info.status = 1; break;
                         case DownloadManager.STATUS_PAUSED: info.status = 2; break;
                         case DownloadManager.STATUS_SUCCESSFUL: info.status = 3; break;
                         case DownloadManager.STATUS_FAILED: info.status = 4; break;
                     }
                     list.add(info);
                 } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        list.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));
        return list;
    }
}
