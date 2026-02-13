package com.olsc.manorbrowser.data;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

// 简单的明文存储。实际应用应加密。
// Simple password storage (plain text for now). 
public class PasswordStorage {
    private static final String FILE_NAME = "passwords.json";

    public static List<PasswordItem> loadPasswords(Context context) {
        List<PasswordItem> items = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return items;

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                PasswordItem item = PasswordItem.fromJson(obj);
                if (item != null) {
                    items.add(item);
                }
            }
            // Sort by most recent
            Collections.sort(items, (p1, p2) -> Long.compare(p2.timestamp, p1.timestamp));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }

    public static void savePassword(Context context, PasswordItem newItem) {
        List<PasswordItem> items = loadPasswords(context);
        
        // Remove old entry for same url+username if exists (update)
        items.removeIf(item -> item.url.equals(newItem.url) && item.username.equals(newItem.username));
        
        items.add(0, newItem);
        saveAll(context, items);
    }

    private static void saveAll(Context context, List<PasswordItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (PasswordItem item : items) {
                JSONObject obj = item.toJson();
                if (obj != null) array.put(obj);
            }
            
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<PasswordItem> getPasswordsForUrl(Context context, String url) {
        List<PasswordItem> all = loadPasswords(context);
        List<PasswordItem> matches = new ArrayList<>();
        if (url == null) return matches;
        
        // Simplified domain check
        String domain = getDomain(url);
        
        for (PasswordItem item : all) {
            if (item.url != null && getDomain(item.url).equals(domain)) {
                matches.add(item);
            }
        }
        return matches;
    }

    private static String getDomain(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
