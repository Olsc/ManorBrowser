package com.olsc.manorbrowser.data;

import org.mozilla.geckoview.GeckoSession;

public class TabInfo {
    public GeckoSession session;
    public String title;
    public String url;
    public android.graphics.Bitmap thumbnail;

    public long id;
    public int scrollY = 0;

    public TabInfo(GeckoSession session) {
        this.id = System.currentTimeMillis();
        this.session = session;
        this.title = null;
        this.url = "about:blank";
    }
}
