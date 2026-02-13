package com.olsc.manorbrowser.data;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class BookmarkStorage {
    private static final String FILE_NAME = "bookmarks.json";

    public static List<BookmarkItem> loadBookmarks(Context context) {
        List<BookmarkItem> bookmarks = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return bookmarks;

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            JSONArray arr = new JSONArray(new String(data));
            for (int i = 0; i < arr.length(); i++) {
                bookmarks.add(BookmarkItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bookmarks;
    }

    public static void saveBookmarks(Context context, List<BookmarkItem> bookmarks) {
        try {
            JSONArray arr = new JSONArray();
            for (BookmarkItem item : bookmarks) {
                arr.put(item.toJson());
            }
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString().getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addBookmark(Context context, BookmarkItem item) {
        List<BookmarkItem> bookmarks = loadBookmarks(context);
        bookmarks.add(item);
        saveBookmarks(context, bookmarks);
    }
}
