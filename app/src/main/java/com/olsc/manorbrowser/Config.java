/**
 * 全局配置类
 * 包含应用内使用的各种常量、SharedPreferences 偏好设置键名、User Agent 字符串等。
 */
package com.olsc.manorbrowser;

public class Config {
    
    // --- User Agent (用户代理) 相关 ---
    /** 默认 User Agent (Firefox Mobile) */
    public static final String DEFAULT_UA = "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0";
    /** Chrome Mobile User Agent */
    public static final String CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    /** 桌面版 User Agent (Windows Firefox) */
    public static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0";

    // --- SharedPreferences 键名 ---
    /** 统一偏好设置文件名 */
    public static final String PREFERENCE_NAME = "theme_prefs";
    /** 兼容旧引用：偏好设置文件名 */
    public static final String PREF_NAME_THEME = "theme_prefs";
    /** 深色模式开关状态 (boolean) */
    public static final String PREF_KEY_DARK_MODE = "is_dark_mode";
    /** 当前选择的搜索引擎 (String) */
    public static final String PREF_KEY_SEARCH_ENGINE = "search_engine_preference";
    /** 当前选择的背景特效类型 (String) */
    public static final String PREF_KEY_BG_EFFECT = "background_effect_preference";
    /** 纯色背景模式下的颜色值 (int) */
    public static final String PREF_KEY_SOLID_BG_COLOR = "solid_bg_color";
    /** 语言选择偏好 (String: "zh", "en", etc.) */
    public static final String PREF_KEY_LANGUAGE = "language_preference";
    /** 隐私协议是否已同意 (boolean) */
    public static final String PREF_KEY_PRIVACY_AGREED = "privacy_agreed";
    /** 是否强制调用系统下载器而非内置下载逻辑 (boolean) */
    public static final String PREF_KEY_USE_SYSTEM_DOWNLOADER = "use_system_downloader";
    /** 强制桌面模式状态 (boolean) */
    public static final String PREF_KEY_DESKTOP_MODE = "desktop_mode";
    /** 底部左侧第一个按钮功能 (String) */
    public static final String PREF_KEY_LEFT_BUTTON_FUNCTION = "left_button_function_v2";
    /** 是否启用 AI 远程助手 (boolean) */
    public static final String PREF_KEY_AI_REMOTE_ENABLED = "ai_remote_enabled";
    /** 自定义背景图片路径 (String) */
    public static final String PREF_KEY_CUSTOM_BG_IMAGE = "custom_bg_image";

    // --- 按钮功能值 ---
    public static final String FUNC_HOME = "home";
    public static final String FUNC_EXTENSIONS = "extensions";
    public static final String FUNC_BOOKMARKS = "bookmarks";
    public static final String FUNC_HISTORY = "history";
    public static final String FUNC_DOWNLOADS = "downloads";
    public static final String FUNC_DESKTOP_MODE = "desktop_mode";
    public static final String FUNC_ADD_BOOKMARK = "add_bookmark";
    public static final String FUNC_THEME = "theme";

    // --- 搜索引擎标识符 ---
    /** 百度搜索 */
    public static final String ENGINE_BAIDU = "baidu";
    /** 谷歌搜索 */
    public static final String ENGINE_GOOGLE = "google";

    // --- URL 常量 ---
    /** 空白页 */
    public static final String URL_BLANK = "about:blank";

    // --- 背景特效类型值 ---
    /** 流星雨特效 */
    public static final String BG_EFFECT_METEOR = "meteor";
    /** 雨滴/窗外小雨特效 */
    public static final String BG_EFFECT_RAIN = "rain";
    /** 雪花/北欧雪景特效 */
    public static final String BG_EFFECT_SNOW = "snow";
    /** 极光/深林星空特效 */
    public static final String BG_EFFECT_AURORA = "aurora";
    /** 樱花/梦幻和风特效 */
    public static final String BG_EFFECT_SAKURA = "sakura";
    /** 静态纯色背景模式 */
    public static final String BG_EFFECT_SOLID = "solid";
    /** 自定义图片背景 */
    public static final String BG_EFFECT_IMAGE = "image";
    
    // --- 默认数值 ---
    /** 默认纯色背景颜色 (曼诺绿) */
    public static final int DEFAULT_SOLID_BG_COLOR = 0xFF00FF99;
}