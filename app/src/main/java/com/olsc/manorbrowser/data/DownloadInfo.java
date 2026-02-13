package com.olsc.manorbrowser.data;

public class DownloadInfo {
    public long id;
    public String title;
    public String url;
    public String filePath;
    public String mimeType;
    public int status;
    public long totalBytes;
    public long currentBytes;
    public long timestamp;
    public int reason;

    public DownloadInfo(long id, String title, String url, String filePath, String mimeType) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.status = 0;
        this.timestamp = System.currentTimeMillis();
    }
}
