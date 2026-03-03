/**
 * Intent 处理辅助类
 * 用于 Activity 跳转、外部应用呼起、DeepLink 处理以及 URI 解析等逻辑。
 */
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
     * 该方法会按优先级检查：Intent Scheme -> 特殊 Scheme -> 应用商店链接 -> 已知 DeepLink。
     *
     * @param context 上下文
     * @param url     要处理的 URL
     * @return true 如果成功处理（已跳转），false 如果应该继续在浏览器中加载
     */
    public static boolean tryOpenExternalApp(Context context, String url) {
        if (url == null || url.isEmpty()) return false;

        // 1. 检查是否是以 intent:// 开头的 Android Intent Scheme
        if (url.startsWith("intent://")) {
            return handleIntentScheme(context, url);
        }

        // 2. 检查是否是已知的非 HTTP 特殊 Scheme (如 tel:, mailto:, alipay: 等)
        if (isSpecialScheme(url)) {
            return handleSpecialScheme(context, url);
        }

        // 3. 检查是否是 Google Play 或 Apple App Store 的链接
        if (isAppStoreLink(url)) {
            return handleAppStoreLink(context, url);
        }

        // 4. 检查是否是已知的应用 DeepLink (可能包含在 HTTP/HTTPS 链接中)
        if (isKnownAppDeepLink(url)) {
            return handleDeepLink(context, url);
        }

        return false;
    }

    /**
     * 判断是否属于特殊协议 Scheme
     */
    private static boolean isSpecialScheme(String url) {
        String[] schemes = {
            "mailto:", "tel:", "sms:", "market:", "geo:",
            "whatsapp:", "tg:", "telegram:", "wechat:", "weixin:",
            "alipay:", "alipays:", "taobao:", "tmall:", "jd:", "openjd:",
            "douyin:", "snssdk1128:", "snssdk1112:",
            "bilibili:", "zhihu:", "weibo:", "sinaweibo:",
            "xhsdiscover:", "xhslink:", "xiaohongshu:",
            "meituan:", "dianping:", "eleme:", "pinduoduo:",
            "baiduboxapp:", "baidumap:", "diditaxi:",
            "youku:", "tenvideo:", "iqiyi:",
            "youtube:", "twitter:", "facebook:", "instagram:", "snapchat:", "tiktok:",
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

    /**
     * 处理特殊协议 Scheme 的跳转逻辑
     */
    private static boolean handleSpecialScheme(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

            if (!activities.isEmpty()) {
                // 如果只有一个匹配项（非浏览器），直接设置包名以确保精准跳转，避免系统选择器
                if (activities.size() == 1) {
                    intent.setPackage(activities.get(0).activityInfo.packageName);
                }
                context.startActivity(intent);
                return true;
            } else {
                // 如果没有找到应用且是 http/https 开头的，则返回 false 让浏览器处理
                if (url.startsWith("http")) {
                    return false;
                }
                Toast.makeText(context, R.string.error_no_app_found, Toast.LENGTH_SHORT).show();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 处理 intent:// 协议
     */
    private static boolean handleIntentScheme(Context context, String url) {
        try {
            // 解析 Intent URI 字符串
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = context.getPackageManager();
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

            if (info != null) {
                // 找到对应的 Activity，直接跳转
                context.startActivity(intent);
                return true;
            } else {
                // 未找到 Activity，尝试获取 fallback URL
                String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    // 交给 MainActivity 的加载逻辑处理
                    return false;
                }

                // 无 fallback URL，尝试跳转至应用商店
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
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 判断是否为应用商店链接
     */
    private static boolean isAppStoreLink(String url) {
        return url.contains("play.google.com/store/apps") ||
               url.contains("apps.apple.com") ||
               url.contains("itunes.apple.com");
    }

    /**
     * 处理应用商店下载链接
     */
    private static boolean handleAppStoreLink(Context context, String url) {
        try {
            // 尝试从 URL 中提取包名 (id=xxx)
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
        return false;
    }

    /**
     * 呼起应用商店详情页
     * 先尝试 market:// 协议，失败则尝试网页版链接
     */
    private static void openAppStore(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 判断是否属于已知的需要拦截并跳转的 DeepLink 域名或协议
     */
    private static boolean isKnownAppDeepLink(String url) {
        String[] patterns = {
            "://open", "://launch", "://app", "://share",
            "weixin://", "alipay://", "taobao://", "tmall://",
            "jd://", "openjd://", "douyin://", "snssdk1128://", "bilibili://", "zhihu://",
            "weibo://", "sinaweibo://", "qq://", "mqqapi://", "tim://",
            "xhsdiscover://", "xhslink://", "xiaohongshu://",
            "xhslink.com", "sns.xhs.com", "xiaohongshu.com",
            "bilibili.com", "b23.tv", "biliapi://",
            "zhihu.com", "zhihu://",
            "douyin.com", "snssdk1128://", "amemv.com",
            "taobao.com", "tmall.com", "jd.com", "pinduoduo.com",
            "meituan.com", "dianping.com", "ele.me"
        };
        String lowerUrl = url.toLowerCase();
        for (String pattern : patterns) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理 DeepLink 的核心逻辑
     */
    private static boolean handleDeepLink(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

            // 过滤掉当前应用及其他常见浏览器，防止在多个浏览器间循环跳转
            activities.removeIf(info -> {
                String packageName = info.activityInfo.packageName;
                return packageName.contains("com.olsc.manorbrowser") ||
                        packageName.contains("browser") ||
                        packageName.contains("chrome") ||
                        packageName.contains("firefox");
            });

            if (!activities.isEmpty()) {
                // 如果过滤后只有一个匹配项，直接设置包名确保精准跳转
                if (activities.size() == 1) {
                    intent.setPackage(activities.get(0).activityInfo.packageName);
                }
                context.startActivity(intent);
                return true;
            } else {
                return false; // 无匹配应用，交还给渲染引擎尝试作为网页加载
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 是否需要拦截该 URL（即是否需要尝试通过外部应用打开）
     * 用于 Webview 的逻辑判断。
     */
    public static boolean shouldInterceptUrl(String url) {
        if (url == null || url.isEmpty()) return false;

        // HTTP/HTTPS 链接通常保留在浏览器中加载，除非被判定为应用商店链接或已知 DeepLink
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return isAppStoreLink(url) || isKnownAppDeepLink(url);
        }

        // 排除掉浏览器内部协议及 Web 内容协议
        return !url.startsWith("about:") &&
               !url.startsWith("data:") &&
               !url.startsWith("javascript:") &&
               !url.startsWith("resource:") &&
               !url.startsWith("chrome:");
    }
}
