/**
 * 浏览器命令协调器
 *
 * 持有 CommandHandler 接口，供 AiCommandClient 调用以控制浏览器行为。
 * 注意：本项目采用"手机主动连接 PC"的反向连接模式，手机无需对外开放端口。
 *
 * 支持的操作由 CommandHandler 定义，包括：
 * - 导航到指定 URL
 * - 异步执行 JavaScript
 * - 获取浏览历史与下载列表
 * - 获取当前页面 URL / 标题 / 状态
 */
package com.olsc.manorbrowser.utils;

import com.olsc.manorbrowser.data.DownloadInfo;
import com.olsc.manorbrowser.data.HistoryStorage;

import java.util.List;

public class BrowserCommandServer {

    // -------------------------------------------------------
    // 命令执行接口 - 由 MainActivity 实现并注入
    // -------------------------------------------------------
    public interface CommandHandler {
        /** 导航到指定 URL */
        void navigate(String url);

        /** 异步执行 JavaScript，通过 callback 返回字符串结果 */
        void evalJs(String js, EvalCallback callback);

        /** 获取浏览历史，可按时间字符串模糊过滤 */
        List<HistoryStorage.HistoryItem> getHistory(String timeFilter);

        /** 获取当前页面 URL */
        String getCurrentUrl();

        /** 获取当前页面标题 */
        String getCurrentTitle();

        /** 获取所有下载记录 */
        List<DownloadInfo> getDownloads();

        /** 获取当前浏览器状态的 JSON 字符串（含 URL + 标题） */
        String getStatus();
    }

    /** JS 执行结果回调 */
    public interface EvalCallback {
        void onResult(String result);
    }

    private final CommandHandler handler;

    public BrowserCommandServer(CommandHandler handler) {
        this.handler = handler;
    }

    /** 获取命令处理器 */
    public CommandHandler getHandler() {
        return handler;
    }
}
