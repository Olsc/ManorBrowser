/**
 * 全局 Application 类
 *
 * 持有 AiCommandClient 单例，确保整个应用生命周期内只有一个轮询实例。
 * 需要在 AndroidManifest.xml 中注册: android:name=".ManorBrowserApp"
 *
 * 设计说明：AI 智能托管不随应用重启自动开启，需用户在设置中手动开启。
 */
package com.olsc.manorbrowser;

import android.app.Application;

import com.olsc.manorbrowser.utils.AiCommandClient;
import com.olsc.manorbrowser.utils.BrowserCommandServer;

public class ManorBrowserApp extends Application {

    private static ManorBrowserApp instance;
    private AiCommandClient aiCommandClient;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    /**
     * 初始化 AI 命令客户端（懒加载，只创建一次）
     * 由 MainActivity 在 initAiRemoteAssistant() 中调用。
     *
     * @param handler 浏览器命令执行器，由 MainActivity 实现
     */
    public void initAiCommandClient(BrowserCommandServer.CommandHandler handler) {
        if (aiCommandClient == null) {
            aiCommandClient = new AiCommandClient(this, handler);
            // 注意：不在此处自动启动，用户需在设置中手动开启智能托管
        }
    }

    public AiCommandClient getAiCommandClient() {
        return aiCommandClient;
    }

    public static ManorBrowserApp getInstance() {
        return instance;
    }
}
