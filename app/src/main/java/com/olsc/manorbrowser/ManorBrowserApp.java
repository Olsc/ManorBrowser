package com.olsc.manorbrowser;

import android.app.Application;
import com.olsc.manorbrowser.utils.AiCommandClient;
import com.olsc.manorbrowser.utils.BrowserCommandServer;

/**
 * 全局 Application 类，持有 AiCommandClient 单例。
 * 需要在 AndroidManifest.xml 中注册: android:name=".ManorBrowserApp"
 */
public class ManorBrowserApp extends Application {
    private static ManorBrowserApp instance;
    private AiCommandClient aiCommandClient;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public void initAiCommandClient(BrowserCommandServer.CommandHandler handler) {
        if (aiCommandClient == null) {
            aiCommandClient = new AiCommandClient(this, handler);
            
            // 是否启用了 AI 远程助手
            boolean enabled = getSharedPreferences(Config.PREFERENCE_NAME, MODE_PRIVATE)
                    .getBoolean(Config.PREF_KEY_AI_REMOTE_ENABLED, false);
            
            // 手机端不再自动启动（用户明确开启后才启动，重启应用需重新开启或维持静默）
            // 按照需求：重启应用不自动进入 AI 控制模式
            /*
            if (enabled && aiCommandClient.getServerUrl() != null && !aiCommandClient.getServerUrl().isEmpty()) {
                aiCommandClient.start();
            }
            */
        }
    }

    public AiCommandClient getAiCommandClient() {
        return aiCommandClient;
    }

    public static ManorBrowserApp getInstance() {
        return instance;
    }
}
