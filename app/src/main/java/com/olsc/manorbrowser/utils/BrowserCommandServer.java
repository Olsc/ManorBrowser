/**
 * 浏览器内置 HTTP 命令服务器
 * 监听 WiFi 端口，接受 JSON 命令，执行后返回 JSON 结果。
 * 无需 ADB、无需 GeckoView 调试 Socket，直接通过局域网控制。
 *
 * 支持的命令 (HTTP POST application/json):
 *   {"action":"ping"}
 *   {"action":"navigate", "url":"https://..."}
 *   {"action":"get_url"}
 *   {"action":"get_source"}
 *   {"action":"find_text", "text":"..."}
 *   {"action":"click", "selector":"#id 或 CSS selector"}
 *   {"action":"set_input", "selector":"...", "value":"..."}
 *   {"action":"eval_js", "js":"..."}
 *   {"action":"get_history", "filter":"2025-05"}
 */
package com.olsc.manorbrowser.utils;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import com.olsc.manorbrowser.data.HistoryStorage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BrowserCommandServer {
    private static final String TAG = "BrowserCmdServer";

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private CommandHandler handler;

    // -------------------------------------------------------
    // 命令执行接口 - 由 MainActivity 实现
    // -------------------------------------------------------
    public interface CommandHandler {
        /** 导航到指定 URL */
        void navigate(String url);

        /** 异步执行 JavaScript，通过 callback 返回字符串结果 */
        void evalJs(String js, EvalCallback callback);

        /** 获取浏览历史，可按时间字符串模糊过滤 */
        List<HistoryStorage.HistoryItem> getHistory(String timeFilter);

        /** 获取当前 URL */
        String getCurrentUrl();

        /** 获取当前页面标题 */
        String getCurrentTitle();

        /** 获取下载列表 */
        List<com.olsc.manorbrowser.data.DownloadInfo> getDownloads();

        String getStatus();
    }

    public interface EvalCallback {
        void onResult(String result);
    }

    // -------------------------------------------------------
    // 启动 / 停止
    // -------------------------------------------------------
    public BrowserCommandServer(CommandHandler handler) {
        this.handler = handler;
    }

    public CommandHandler getHandler() {
        return handler;
    }

    /**
     * @return 是否启动成功
     */
    public boolean start(int port) {
        try {
            serverSocket = new ServerSocket(port, 10, InetAddress.getByName("0.0.0.0"));
            serverSocket.setReuseAddress(true);
            running = true;
            serverThread = new Thread(this::acceptLoop, "BrowserCmdServer");
            serverThread.setDaemon(true);
            serverThread.start();
            Log.i(TAG, "Command server started on port " + port);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        Log.i(TAG, "Command server stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    // -------------------------------------------------------
    // 接受客户端连接
    // -------------------------------------------------------
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client), "BrowserCmdClient").start();
            } catch (Exception e) {
                if (running) {
                    Log.w(TAG, "Accept interrupted: " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------
    // 处理 HTTP 请求
    // -------------------------------------------------------
    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8"));
            OutputStream out = client.getOutputStream();

            // 读取 HTTP 请求头
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 读取请求体
            String requestBody = "";
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                int read = reader.read(buffer, 0, contentLength);
                requestBody = new String(buffer, 0, Math.max(read, 0));
            }

            // 分发命令
            String responseBody = processCommand(requestBody.trim());
            byte[] responseBytes = responseBody.getBytes("UTF-8");

            String httpResponse =
                "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + responseBytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n";
            out.write(httpResponse.getBytes("UTF-8"));
            out.write(responseBytes);
            out.flush();

        } catch (Exception e) {
            Log.w(TAG, "Client handling error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------
    // 执行 JSON 命令
    // -------------------------------------------------------
    private String processCommand(String body) {
        try {
            if (body == null || body.isEmpty()) {
                return ok("pong");
            }

            JSONObject cmd = new JSONObject(body);
            String action = cmd.optString("action", "");

            switch (action) {
                case "ping":
                    return ok("pong");

                case "get_url":
                    return ok(handler.getCurrentUrl());

                case "get_title":
                    return ok(handler.getCurrentTitle());

                case "navigate": {
                    String url = cmd.optString("url", "about:blank");
                    handler.navigate(url);
                    return ok("navigating");
                }

                case "get_source": {
                    String result = evalSync("document.documentElement.outerHTML", 15);
                    return ok(result);
                }

                case "find_text": {
                    String text = cmd.optString("text", "");
                    String escapedText = text.replace("'", "\\'").replace("\\", "\\\\");
                    String js = "(function() {" +
                        "  var results = [];" +
                        "  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);" +
                        "  while (walker.nextNode()) {" +
                        "    var n = walker.currentNode;" +
                        "    if (n.textContent.toLowerCase().includes('" + escapedText.toLowerCase() + "')) {" +
                        "      var el = n.parentElement;" +
                        "      var rect = el.getBoundingClientRect();" +
                        "      if (rect.width > 0 && rect.height > 0) {" +
                        "        results.push({tag: el.tagName, text: el.innerText.trim().substring(0, 150)," +
                        "          x: Math.round(rect.left + window.scrollX), y: Math.round(rect.top + window.scrollY)," +
                        "          w: Math.round(rect.width), h: Math.round(rect.height)});" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "  return JSON.stringify(results.slice(0, 30));" +
                        "})()";
                    String raw = evalSync(js, 15);
                    String cleaned = unquoteJsonString(raw);
                    return new JSONObject().put("status", "ok").put("data", new JSONArray(cleaned)).toString();
                }

                case "find_buttons": {
                    String text = cmd.optString("text", "");
                    String escapedText = text.replace("'", "\\'");
                    String js = "(function() {" +
                        "  var els = document.querySelectorAll('button, input[type=submit], input[type=button], a[role=button], [class*=btn]');" +
                        "  var results = [];" +
                        "  for (var i = 0; i < els.length; i++) {" +
                        "    var el = els[i];" +
                        "    var t = (el.innerText || el.value || el.getAttribute('aria-label') || '').trim();" +
                        "    if ('" + escapedText + "' === '' || t.toLowerCase().includes('" + escapedText.toLowerCase() + "')) {" +
                        "      var rect = el.getBoundingClientRect();" +
                        "      if (rect.width > 0) results.push({tag:el.tagName, text:t.substring(0,80)," +
                        "        id:el.id, cls:el.className.substring(0,50)," +
                        "        x:Math.round(rect.left+window.scrollX), y:Math.round(rect.top+window.scrollY)," +
                        "        w:Math.round(rect.width), h:Math.round(rect.height)});" +
                        "    }" +
                        "    if (results.length >= 20) break;" +
                        "  }" +
                        "  return JSON.stringify(results);" +
                        "})()";
                    String raw = evalSync(js, 15);
                    String cleaned = unquoteJsonString(raw);
                    return new JSONObject().put("status", "ok").put("data", new JSONArray(cleaned)).toString();
                }

                case "click": {
                    String selector = cmd.optString("selector", "");
                    String result = evalSync("(function(){var el=document.querySelector('" + selector.replace("'", "\\'") + "'); if(!el)return 'not_found'; el.click(); return 'clicked';})()", 10);
                    return ok(result);
                }

                case "click_by_text": {
                    String text = cmd.optString("text", "");
                    String escapedText = text.replace("'", "\\'");
                    String js = "(function() {" +
                        "  var els = document.querySelectorAll('button, input[type=submit], a');" +
                        "  for (var i = 0; i < els.length; i++) {" +
                        "    var t = (els[i].innerText || els[i].value || '').trim().toLowerCase();" +
                        "    if (t.includes('" + escapedText.toLowerCase() + "')) {" +
                        "      els[i].click();" +
                        "      return 'clicked: ' + t.substring(0,80);" +
                        "    }" +
                        "  }" +
                        "  return 'not_found';" +
                        "})()";
                    String result = evalSync(js, 10);
                    return ok(result);
                }

                case "set_input": {
                    String selector = cmd.optString("selector", "");
                    String value = cmd.optString("value", "");
                    String js = "(function() {" +
                        "  var el = document.querySelector('" + selector.replace("'", "\\'") + "');" +
                        "  if (!el) return 'not_found';" +
                        "  el.value = '" + value.replace("'", "\\'").replace("\\", "\\\\") + "';" +
                        "  el.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "  el.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "  return 'ok';" +
                        "})()";
                    return ok(evalSync(js, 10));
                }

                case "eval_js": {
                    return ok(evalSync(cmd.optString("js", "undefined"), 15));
                }

                case "get_history": {
                    String filter = cmd.optString("filter", "");
                    List<HistoryStorage.HistoryItem> items = handler.getHistory(filter);
                    JSONArray arr = new JSONArray();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    for (HistoryStorage.HistoryItem item : items) {
                        JSONObject o = new JSONObject();
                        o.put("title", item.title);
                        o.put("url", item.url);
                        o.put("time", sdf.format(new Date(item.timestamp)));
                        o.put("timestamp", item.timestamp);
                        arr.put(o);
                    }
                    return new JSONObject().put("status", "ok").put("data", arr).toString();
                }

                case "get_downloads": {
                    List<com.olsc.manorbrowser.data.DownloadInfo> items = handler.getDownloads();
                    JSONArray arr = new JSONArray();
                    for (com.olsc.manorbrowser.data.DownloadInfo item : items) {
                        JSONObject o = new JSONObject();
                        o.put("id", item.id);
                        o.put("title", item.title);
                        o.put("url", item.url);
                        o.put("status", item.status);
                        o.put("total", item.totalBytes);
                        o.put("current", item.currentBytes);
                        o.put("path", item.filePath);
                        arr.put(o);
                    }
                    return new JSONObject().put("status", "ok").put("data", arr).toString();
                }

                case "get_status": {
                    JSONObject status = new JSONObject();
                    status.put("url", handler.getCurrentUrl());
                    status.put("title", handler.getCurrentTitle());
                    return new JSONObject().put("status", "ok").put("data", status).toString();
                }

                default:
                    return error("Unknown action: " + action);
            }
        } catch (Exception e) {
            Log.e("BrowserCommandServer", "ProcessCommand error", e);
            return error(e.getMessage());
        }
    }

    // -------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------
    /** 同步执行 JS，最多等待 timeoutSec 秒 */
    private String evalSync(String js, int timeoutSec) {
        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {""};
        handler.evalJs(js, r -> {
            result[0] = (r != null) ? r : "null";
            latch.countDown();
        });
        try {
            latch.await(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        return result[0];
    }

    /** GeckoView 返回的字符串可能被包了一层 JSON 引号，去掉 */
    private String unquoteJsonString(String s) {
        if (s == null) return "[]";
        String trimmed = s.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            // 先解析为 JSON 字符串
            try {
                return new JSONObject("{\"v\":" + trimmed + "}").getString("v");
            } catch (Exception ignored) {}
        }
        return trimmed;
    }

    private String ok(String data) {
        try {
            return new JSONObject()
                .put("status", "ok")
                .put("data", data)
                .toString();
        } catch (Exception e) {
            return "{\"status\":\"ok\",\"data\":\"\"}";
        }
    }

    private String error(String msg) {
        try {
            return new JSONObject()
                .put("status", "error")
                .put("message", msg != null ? msg : "unknown error")
                .toString();
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"unknown\"}";
        }
    }
}
