/**
 * 搜索引擎与地址解析辅助类
 *
 * 负责解析地址栏输入内容：判断是 URL、域名、IP 还是搜索关键词，
 * 并根据用户设置的搜索引擎生成最终跳转地址。
 */
package com.olsc.manorbrowser.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Patterns;
import android.webkit.URLUtil;
import com.olsc.manorbrowser.Config;

public class SearchHelper {

    /**
     * 根据地址栏输入内容生成最终跳转 URL
     *
     * @param context 上下文，用于读取搜索引擎偏好
     * @param query   用户输入的原始字符串
     * @return 最终的 URL 字符串
     */
    public static String getSearchUrl(Context context, String query) {
        query = query.trim();

        // 1. 已包含协议前缀，直接返回原始 URL
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.startsWith("http://") || lowerQuery.startsWith("https://") ||
            lowerQuery.startsWith("file://") || lowerQuery.startsWith("about:") ||
            lowerQuery.startsWith("javascript:") || lowerQuery.startsWith("data:")) {
            return query;
        }

        // 2. 局域网地址或 IP 格式，使用 http:// 以避免强制 HTTPS 跳转失败
        if (isLocalOrIpFormat(query)) {
            return "http://" + query;
        }

        // 3. 常规公网域名（如 google.com），优先使用 HTTPS
        if (Patterns.WEB_URL.matcher(query).matches()) {
            return "https://" + query;
        }

        // 4. 否则视为搜索关键词，拼接对应搜索引擎 URL
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String engine = prefs.getString(Config.PREF_KEY_SEARCH_ENGINE, Config.ENGINE_BAIDU);

        if (Config.ENGINE_GOOGLE.equals(engine)) {
            return "https://www.google.com/search?q=" + query;
        } else {
            return "https://www.baidu.com/s?wd=" + query;
        }
    }

    /**
     * 判断输入是否为局域网地址、IP 地址或直连主机名
     */
    private static boolean isLocalOrIpFormat(String input) {
        if (input == null || input.isEmpty()) return false;

        // 分离 Host 和路径/端口
        String hostPart = input.split("/")[0].trim();

        // 移除端口号，单独校验主机名
        String ipOrHost = hostPart;
        if (hostPart.contains(":")) {
            try {
                ipOrHost = hostPart.split(":")[0];
            } catch (Exception ignored) {}
        }

        // IPv4 地址
        if (Patterns.IP_ADDRESS.matcher(ipOrHost).matches()) return true;

        // localhost 或 127.0.0.1
        if ("localhost".equalsIgnoreCase(ipOrHost) || "127.0.0.1".equals(ipOrHost)) return true;

        // 局域网常见后缀
        String lowHost = ipOrHost.toLowerCase();
        if (lowHost.endsWith(".local") || lowHost.endsWith(".lan") ||
            lowHost.endsWith(".home") || lowHost.endsWith(".internal")) return true;

        // 带端口号的纯主机名（如 nas:5000），通常为内网服务
        if (hostPart.contains(":") && !ipOrHost.contains(".")) return true;

        // IPv6 地址（如 [::1] 或 [fe80:...]）
        if (ipOrHost.startsWith("[") && ipOrHost.endsWith("]")) return true;

        return false;
    }
}
