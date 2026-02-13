package com.olsc.manorbrowser.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Patterns;
import android.webkit.URLUtil;

import com.olsc.manorbrowser.Config;

public class SearchHelper {

    public static String getSearchUrl(Context context, String query) {
        query = query.trim();

        if (URLUtil.isValidUrl(query)) {
            return query;
        }

        if (Patterns.IP_ADDRESS.matcher(query).matches()) {
            return "http://" + query;
        }

        if (Patterns.WEB_URL.matcher(query).matches()) {
             return "https://" + query;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String engine = prefs.getString(Config.PREF_KEY_SEARCH_ENGINE, Config.ENGINE_BAIDU);

        if (Config.ENGINE_GOOGLE.equals(engine)) {
            return "https://www.google.com/search?q=" + query;
        } else {
            return "https://www.baidu.com/s?wd=" + query;
        }
    }
}
