package com.olsc.manorbrowser.data;

public class PasswordItem {
    public String url;
    public String username;
    public String password;
    public long timestamp;

    public PasswordItem(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.timestamp = System.currentTimeMillis();
    }

    public org.json.JSONObject toJson() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("url", url);
            json.put("username", username);
            json.put("password", password);
            json.put("timestamp", timestamp);
            return json;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    public static PasswordItem fromJson(org.json.JSONObject json) {
        if (json == null) return null;
        return new PasswordItem(
            json.optString("url"),
            json.optString("username"),
            json.optString("password")
        );
    }
}
