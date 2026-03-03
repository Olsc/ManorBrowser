/**
 * 文件操作工具类
 *
 * 提供 Content URI 与本地文件之间的拷贝操作，
 * 主要用于扩展安装（.xpi）和自定义背景图片的保存。
 */
package com.olsc.manorbrowser.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtil {

    /**
     * 将 Content URI 指向的文件拷贝到应用缓存目录下的临时文件
     * 主要用于安装扩展（.xpi 文件）
     */
    @SuppressLint("SetWorldReadable")
    public static File copyContentUriToTempFile(Context context, Uri uri, String filename) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File tempDir = new File(context.getCacheDir(), "extensions");
            if (!tempDir.exists()) tempDir.mkdirs();
            // 扩展目录需要对 GeckoView 可读
            tempDir.setExecutable(true, false);
            tempDir.setReadable(true, false);
            File tempFile = new File(tempDir, filename != null ? filename : "temp.xpi");
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
            is.close();
            tempFile.setReadable(true, false);
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将 Content URI 指向的文件拷贝到指定目标文件
     * 主要用于保存用户选取的自定义背景图片
     */
    public static boolean copyUriToFile(Context context, Uri uri, File destFile) {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            if (is == null) return false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
