package com.olsc.manorbrowser.data;

import com.olsc.manorbrowser.Config;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class TabStorage {

    private static final String FILE_NAME = "tabs.json";
    private static final String THUMB_DIR = "thumbnails";

    public static void saveTabs(Context context, List<TabInfo> tabs) {
        new Thread(() -> {
            try {

                JSONArray jsonArray = new JSONArray();
                for (TabInfo tab : tabs) {
                    JSONObject json = new JSONObject();
                    json.put("id", tab.id);
                    json.put("url", tab.url);
                    json.put("title", tab.title);
                    jsonArray.put(json);

                    saveThumbnail(context, tab);
                }

                File file = new File(context.getFilesDir(), FILE_NAME);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(jsonArray.toString().getBytes());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static List<TabInfo> loadTabs(Context context) {
        List<TabInfo> tabs = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return tabs;

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonStr = new String(data);
            JSONArray jsonArray = new JSONArray(jsonStr);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                TabInfo tab = new TabInfo(null); 
                tab.id = json.optLong("id", System.currentTimeMillis());
                tab.url = json.optString("url", Config.URL_BLANK);
                tab.title = json.optString("title", "New Tab");
                tab.thumbnail = loadThumbnail(context, tab.id);
                tabs.add(tab);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tabs;
    }

    private static void saveThumbnail(Context context, TabInfo tab) {
        if (tab.thumbnail == null) return;
        try {
            File dir = new File(context.getFilesDir(), THUMB_DIR);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, tab.id + ".png");
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                tab.thumbnail.compress(Bitmap.CompressFormat.PNG, 80, bos);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Bitmap loadThumbnail(Context context, long id) {
        try {
            File file = new File(new File(context.getFilesDir(), THUMB_DIR), id + ".png");
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

