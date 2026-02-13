package com.olsc.manorbrowser.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BookmarkItem {
    public enum Type { LINK, FOLDER }

    public String title;
    public String url; // Only for LINK
    public long id;
    public Type type;
    public List<BookmarkItem> children; // Only for FOLDER
    public long parentId;

    public BookmarkItem(String title, String url) {
        this.title = title;
        this.url = url;
        this.id = System.currentTimeMillis() + (long)(Math.random() * 1000);
        this.type = Type.LINK;
        this.parentId = -1;
    }

    public BookmarkItem(String title) {
        this.title = title;
        this.id = System.currentTimeMillis() + (long)(Math.random() * 1000);
        this.type = Type.FOLDER;
        this.children = new ArrayList<>();
        this.parentId = -1;
    }

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("type", type.name());
        json.put("parentId", parentId);
        if (type == Type.LINK) {
            json.put("url", url);
        } else {
            JSONArray arr = new JSONArray();
            for (BookmarkItem child : children) {
                arr.put(child.toJson());
            }
            json.put("children", arr);
        }
        return json;
    }

    public static BookmarkItem fromJson(JSONObject json) throws Exception {
        Type type = Type.valueOf(json.getString("type"));
        BookmarkItem item;
        if (type == Type.LINK) {
            item = new BookmarkItem(json.getString("title"), json.getString("url"));
        } else {
            item = new BookmarkItem(json.getString("title"));
            JSONArray arr = json.optJSONArray("children");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    BookmarkItem child = fromJson(arr.getJSONObject(i));
                    child.parentId = item.id;
                    item.children.add(child);
                }
            }
        }
        item.id = json.getLong("id");
        item.parentId = json.optLong("parentId", -1);
        return item;
    }
}
