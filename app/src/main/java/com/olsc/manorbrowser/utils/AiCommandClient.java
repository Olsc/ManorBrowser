/**
 * AI 命令客户端
 *
 * 采用"手机主动连接 PC 服务端"的反向连接模式：
 *   1. 用户在设置中配置 PC 的 IP 地址（服务端地址）
 *   2. 本客户端每 1.5 秒向服务端 POST /api/phone/poll 获取待执行命令
 *   3. 执行命令后，将结果通过 POST /api/phone/result 上报给服务端
 *   4. 服务端根据命令结果继续驱动 AI 推理
 *
 * 无需知道手机 IP，无需 ADB，手机端主动发起连接。
 */
package com.olsc.manorbrowser.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AiCommandClient {
    private static final String TAG = "AiCmdClient";

    // SharedPreferences 文件名和键名（独立文件，避免与通用配置混淆）
    private static final String PREF_NAME = "ai_agent_prefs";
    private static final String PREF_SERVER_URL = "server_url";

    // 网络超时时长（毫秒）
    private static final int TIMEOUT_MS = 10_000;
    // 轮询间隔（毫秒）
    private static final int POLL_INTERVAL_MS = 1500;

    private final Context context;
    private final BrowserCommandServer.CommandHandler handler;
    private ScheduledExecutorService executor;

    private volatile boolean running = false;
    private volatile String serverUrl = null;
    private volatile boolean lastPollSuccessful = false;

    public AiCommandClient(Context context, BrowserCommandServer.CommandHandler handler) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        // 初始化时读取上次保存的服务端地址
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.serverUrl = prefs.getString(PREF_SERVER_URL, null);
    }

    // -------------------------------------------------------
    // 公开方法
    // -------------------------------------------------------

    /** 设置并持久化服务端 URL（自动去除末尾斜杠） */
    public void setServerUrl(String url) {
        if (url != null) url = url.trim().replaceAll("/$", "");
        this.serverUrl = url;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SERVER_URL, url)
            .apply();
        Log.i(TAG, "服务端地址已更新: " + url);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    /** 启动后台轮询线程 */
    public void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AiCmdPoller");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Log.i(TAG, "开始轮询: " + serverUrl);
    }

    /** 停止后台轮询线程 */
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        Log.i(TAG, "轮询已停止");
    }

    public boolean isRunning() {
        return running;
    }

    /** 最近一次轮询是否成功（用于 UI 显示连接状态） */
    public boolean isLastPollSuccessful() {
        return lastPollSuccessful;
    }

    // -------------------------------------------------------
    // 核心轮询逻辑
    // -------------------------------------------------------

    private void poll() {
        if (!running || serverUrl == null || serverUrl.isEmpty()) return;
        try {
            String resp = doPost(serverUrl + "/api/phone/poll", buildPhoneInfo().toString());
            if (resp == null || resp.isEmpty()) {
                lastPollSuccessful = false;
                return;
            }

            JSONObject respObj = new JSONObject(resp);
            if (!"ok".equals(respObj.optString("status"))) {
                lastPollSuccessful = false;
                return;
            }

            lastPollSuccessful = true;

            JSONObject cmd = respObj.optJSONObject("command");
            if (cmd == null) return; // 无待执行命令，正常返回

            String cmdId = cmd.getString("id");
            String action = cmd.getString("action");
            JSONObject params = cmd.optJSONObject("params");
            if (params == null) params = new JSONObject();

            Log.d(TAG, "收到命令: " + action + " id=" + cmdId);
            String result = executeCommand(action, params);
            reportResult(cmdId, action, result);

        } catch (Exception e) {
            lastPollSuccessful = false;
            Log.d(TAG, "轮询异常（等待下次重试）: " + e.getMessage());
        }
    }

    /** 构建发送给服务端的手机信息 */
    private JSONObject buildPhoneInfo() throws Exception {
        JSONObject info = new JSONObject();
        info.put("client", "manor-browser");
        return info;
    }

    // -------------------------------------------------------
    // 命令执行（委托给 MainActivity 的 CommandHandler）
    // -------------------------------------------------------

    private String executeCommand(String action, JSONObject params) {
        try {
            switch (action) {
                case "navigate": {
                    String url = params.getString("url");
                    handler.navigate(url);
                    Thread.sleep(600); // 等待导航初始化
                    return jsonOk("navigating");
                }
                case "get_source": {
                    String html = evalSync("document.documentElement.outerHTML", 15);
                    return jsonOk(html);
                }
                case "get_status": {
                    String status = handler.getStatus();
                    return new JSONObject().put("status", "ok").put("data", new JSONObject(status)).toString();
                }
                case "find_text": {
                    String text = params.optString("text", "");
                    String esc = text.replace("'", "\\'");
                    String js = "(function(){var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false),results=[];" +
                        "while(walker.nextNode()){var n=walker.currentNode;if(n.textContent.toLowerCase().includes('" + esc.toLowerCase() + "')){" +
                        "var el=n.parentElement,r=el.getBoundingClientRect();if(r.width>0&&r.height>0)" +
                        "results.push({tag:el.tagName,text:el.innerText.trim().substring(0,120),x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)});}" +
                        "}return JSON.stringify(results.slice(0,20));})()";
                    return jsonOk(evalSync(js, 10));
                }
                case "find_buttons": {
                    String text = params.optString("text", "");
                    String esc = text.replace("'", "\\'");
                    String js = "(function(){var els=document.querySelectorAll('button,input[type=submit],input[type=button],a[role=button],[class*=btn],a'),results=[];" +
                        "for(var i=0;i<els.length&&results.length<20;i++){var el=els[i],t=(el.innerText||el.value||'').trim();" +
                        "if('" + esc + "'===''||t.toLowerCase().includes('" + esc.toLowerCase() + "')){" +
                        "var r=el.getBoundingClientRect();if(r.width>0)results.push({tag:el.tagName,text:t.substring(0,80),id:el.id,x:Math.round(r.left),y:Math.round(r.top)});}}" +
                        "return JSON.stringify(results);})()";
                    return jsonOk(evalSync(js, 10));
                }
                case "click": {
                    String sel = params.optString("selector", "").replace("'", "\\'");
                    String js = "(function(){var el=document.querySelector('" + sel + "');if(!el)return 'not_found';el.click();return 'clicked';})()";
                    return jsonOk(evalSync(js, 8));
                }
                case "click_by_text": {
                    String text = params.optString("text", "").replace("'", "\\'");
                    String js = "(function(){var els=document.querySelectorAll('button,input[type=submit],a');" +
                        "for(var i=0;i<els.length;i++){var t=(els[i].innerText||els[i].value||'').toLowerCase();" +
                        "if(t.includes('" + text.toLowerCase() + "')){els[i].click();return 'clicked:'+t.substring(0,60);}}" +
                        "return 'not_found';})()";
                    return jsonOk(evalSync(js, 8));
                }
                case "set_input": {
                    String sel = params.optString("selector", "").replace("'", "\\'");
                    String val = params.optString("value", "").replace("'", "\\'").replace("\\", "\\\\");
                    String js = "(function(){var el=document.querySelector('" + sel + "');if(!el)return 'not_found';" +
                        "el.value='" + val + "';" +
                        "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                        "return 'ok';})()";
                    return jsonOk(evalSync(js, 8));
                }
                case "eval_js": {
                    String js = params.optString("js", "undefined");
                    return jsonOk(evalSync(js, 15));
                }
                case "get_history": {
                    String filter = params.optString("filter", "");
                    java.util.List<com.olsc.manorbrowser.data.HistoryStorage.HistoryItem> items = handler.getHistory(filter);
                    JSONArray arr = new JSONArray();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    for (com.olsc.manorbrowser.data.HistoryStorage.HistoryItem item : items) {
                        JSONObject o = new JSONObject();
                        o.put("title", item.title);
                        o.put("url", item.url);
                        o.put("time", sdf.format(new Date(item.timestamp)));
                        arr.put(o);
                    }
                    return new JSONObject().put("status", "ok").put("data", arr).toString();
                }
                case "get_downloads": {
                    java.util.List<com.olsc.manorbrowser.data.DownloadInfo> items = handler.getDownloads();
                    JSONArray arr = new JSONArray();
                    for (com.olsc.manorbrowser.data.DownloadInfo item : items) {
                        JSONObject o = new JSONObject()
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("url", item.url)
                            .put("status", item.status)
                            .put("total", item.totalBytes)
                            .put("current", item.currentBytes)
                            .put("path", item.filePath);
                        arr.put(o);
                    }
                    return new JSONObject().put("status", "ok").put("data", arr).toString();
                }
                case "ping":
                    return jsonOk("pong");
                default:
                    return jsonError("未知命令: " + action);
            }
        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /** 上报命令执行结果到服务端 */
    private void reportResult(String cmdId, String action, String result) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("id", cmdId);
            payload.put("action", action);
            payload.put("result", result);
            doPost(serverUrl + "/api/phone/result", payload.toString());
        } catch (Exception e) {
            Log.w(TAG, "上报结果失败: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------

    /**
     * 同步执行 JS，内部通过 CountDownLatch 阻塞等待回调，最多等 timeoutSec 秒。
     */
    private String evalSync(String js, int timeoutSec) {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        String[] result = {""};
        handler.evalJs(js, r -> {
            result[0] = unquoteJson(r);
            latch.countDown();
        });
        try {
            latch.await(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        return result[0];
    }

    /**
     * GeckoView 返回的 JS 结果可能被包一层 JSON 引号，此方法负责去掉外层引号。
     */
    private String unquoteJson(String s) {
        if (s == null || s.length() < 2) return s;
        if (s.startsWith("\"") && s.endsWith("\"")) {
            try {
                Object v = new org.json.JSONTokener(s).nextValue();
                return (v != null) ? v.toString() : s;
            } catch (Exception e) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    /** 发起 HTTP POST 请求，返回响应体字符串（非 200 返回 null） */
    private String doPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            Log.w(TAG, "HTTP 错误 " + code + " from " + urlStr);
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String jsonOk(String data) {
        try {
            return new JSONObject().put("status", "ok").put("data", data).toString();
        } catch (Exception e) {
            return "{\"status\":\"ok\",\"data\":\"\"}";
        }
    }

    private String jsonError(String msg) {
        try {
            return new JSONObject().put("status", "error").put("message", msg != null ? msg : "unknown").toString();
        } catch (Exception e) {
            return "{\"status\":\"error\"}";
        }
    }
}
