package com.olsc.manorbrowser.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;
import com.olsc.manorbrowser.R;
import java.net.URISyntaxException;
import java.util.List;

public class IntentHelper {

    /**
     * 尝试打开外部应用链接
     * @return true 如果成功处理，false 如果应该在浏览器中打开
     */
    public static boolean tryOpenExternalApp(Context context, String url) {
        if (url == null || url.isEmpty()) return false;

        // 1. 检查是否是特殊scheme
        if (isSpecialScheme(url)) {
            return handleSpecialScheme(context, url);
        }

        // 2. 检查是否是intent scheme
        if (url.startsWith("intent://")) {
            return handleIntentScheme(context, url);
        }

        // 3. 检查是否是应用商店链接
        if (isAppStoreLink(url)) {
            return handleAppStoreLink(context, url);
        }

        // 4. 检查是否是已知的应用deep link
        if (isKnownAppDeepLink(url)) {
            return handleDeepLink(context, url);
        }

        return false;
    }

    private static boolean isSpecialScheme(String url) {
        String[] schemes = {
            "mailto:", "tel:", "sms:", "market:", "geo:",
            "whatsapp:", "tg:", "telegram:", "wechat:", "weixin:",
            "alipay:", "alipays:", "taobao:", "jd:", "douyin:",
            "bilibili:", "zhihu:", "weibo:", "youtube:", "twitter:",
            "facebook:", "instagram:", "snapchat:", "tiktok:",
            "spotify:", "netflix:", "maps:"
        };

        String lowerUrl = url.toLowerCase();
        for (String scheme : schemes) {
            if (lowerUrl.startsWith(scheme)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleSpecialScheme(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            
            if (activities.size() > 0) {
                context.startActivity(intent);
                return true;
            } else {
                Toast.makeText(context, R.string.error_no_app_found, Toast.LENGTH_SHORT).show();
                return true; // 已处理，不在浏览器中打开
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.error_open_app_failed, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private static boolean handleIntentScheme(Context context, String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PackageManager pm = context.getPackageManager();
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            
            if (info != null) {
                context.startActivity(intent);
                return true;
            } else {
                // 尝试获取fallback URL
                String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    // 在浏览器中打开fallback URL
                    return false;
                }
                
                // 尝试打开应用商店
                String packageName = intent.getPackage();
                if (packageName != null && !packageName.isEmpty()) {
                    openAppStore(context, packageName);
                    return true;
                }
                
                Toast.makeText(context, R.string.error_no_app_found, Toast.LENGTH_SHORT).show();
                return true;
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.error_invalid_uri, Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isAppStoreLink(String url) {
        return url.contains("play.google.com/store/apps") ||
               url.contains("apps.apple.com") ||
               url.contains("itunes.apple.com");
    }

    private static boolean handleAppStoreLink(Context context, String url) {
        try {
            // 提取包名
            String packageName = null;
            if (url.contains("id=")) {
                int start = url.indexOf("id=") + 3;
                int end = url.indexOf("&", start);
                if (end == -1) end = url.length();
                packageName = url.substring(start, end);
            }

            if (packageName != null && !packageName.isEmpty()) {
                openAppStore(context, packageName);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 如果提取失败，在浏览器中打开
        return false;
    }

    private static void openAppStore(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // 如果没有应用商店，使用浏览器打开
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private static boolean isKnownAppDeepLink(String url) {
        String[] patterns = {
            "://open", "://launch", "://app", "://share",
            "weixin://", "alipay://", "taobao://", "tmall://",
            "jd://", "douyin://", "bilibili://", "zhihu://",
            "weibo://", "qq://", "mqqapi://", "tim://"
        };

        String lowerUrl = url.toLowerCase();
        for (String pattern : patterns) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleDeepLink(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            
            // 过滤掉浏览器
            activities.removeIf(info -> {
                String packageName = info.activityInfo.packageName;
                return packageName.contains("browser") || 
                       packageName.contains("chrome") ||
                       packageName.contains("firefox");
            });
            
            if (activities.size() > 0) {
                context.startActivity(intent);
                return true;
            } else {
                Toast.makeText(context, R.string.error_no_app_found, Toast.LENGTH_SHORT).show();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查URL是否应该被拦截并在外部应用中打开
     */
    public static boolean shouldInterceptUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        
        // HTTP/HTTPS链接通常在浏览器中打开
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // 但某些特殊的HTTP链接应该被拦截
            return isAppStoreLink(url) || isKnownAppDeepLink(url);
        }
        
        // 其他scheme通常应该被拦截
        return !url.startsWith("about:") && 
               !url.startsWith("data:") && 
               !url.startsWith("javascript:");
    }
}
