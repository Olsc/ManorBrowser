package com.olsc.manorbrowser.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.activity.MainActivity;
import com.olsc.manorbrowser.adapter.AiChatAdapter;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 覆盖层管理器
 * 负责管理 AI 助手的 UI 交互、网络通信以及与浏览器核心的协同。
 */
public class AiOverlayManager {
    private static final String TAG = "AiOverlayMgr";

    private final Activity activity;

    /** 6dp 像素值（动画位移用） */
    private static float dp6(Activity activity) {
        return 6 * activity.getResources().getDisplayMetrics().density;
    }
    private final AiCommandClient aiClient;
    private final BrowserCommandServer.CommandHandler handler;

    private View rootOverlay;
    private RecyclerView rvChat;
    private AiChatAdapter chatAdapter;
    private EditText etInput;
    private ImageButton btnSend;
    private View btnExit;
    private TextView tvCurrentPage;
    private View aiStatusDot;
    private LinearLayout thinkingBar;
    private TextView tvThinkingBarText;
    private View aiContentContainer;

    private ExecutorService networkExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRequesting = false;
    private volatile HttpURLConnection currentConn;

    public AiOverlayManager(Activity activity, View rootOverlay, AiCommandClient aiClient, BrowserCommandServer.CommandHandler handler) {
        this.activity = activity;
        this.rootOverlay = rootOverlay;
        this.aiClient = aiClient;
        this.handler = handler;

        initViews();
        setupListeners();
        networkExecutor = Executors.newCachedThreadPool();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        rvChat = rootOverlay.findViewById(R.id.rv_ai_chat);
        etInput = rootOverlay.findViewById(R.id.et_ai_input);
        btnSend = rootOverlay.findViewById(R.id.btn_ai_send);
        btnExit = rootOverlay.findViewById(R.id.btn_exit_ai);
        tvCurrentPage = rootOverlay.findViewById(R.id.tv_ai_current_page);
        aiStatusDot = rootOverlay.findViewById(R.id.ai_status_dot);
        thinkingBar = rootOverlay.findViewById(R.id.ai_thinking_bar);
        tvThinkingBarText = rootOverlay.findViewById(R.id.tv_ai_thinking);
        aiContentContainer = rootOverlay.findViewById(R.id.ai_content_container);

        chatAdapter = new AiChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(activity);
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(chatAdapter);

        // 彻底解决 AI 输出文本时气泡乱闪、动画“打架”的问题
        // 在流式输出频繁刷新时，禁用动画是最高效的方案
        if (rvChat.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) rvChat.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        rvChat.setItemAnimator(null);

        // 处理键盘弹出和系统状态栏避让
        // 将内边距应用到内容容器而非根节点，使背景 View 能填满整个屏幕（包括状态栏）
        ViewCompat.setOnApplyWindowInsetsListener(aiContentContainer, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, imeInsets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * 设置事件监听器
     */
    private void setupListeners() {
        btnExit.setOnClickListener(v -> {
            if (aiClient != null) {
                aiClient.stop();
            }
            activity.getSharedPreferences(com.olsc.manorbrowser.Config.PREF_NAME_THEME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(com.olsc.manorbrowser.Config.PREF_KEY_AI_REMOTE_ENABLED, false)
                    .apply();
            hide();
        });

        btnSend.setOnClickListener(v -> {
            if (isRequesting) {
                stopTask();
            } else {
                sendMessage();
            }
        });

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    /**
     * 显示 AI 覆盖层（材质化淡入）
     */
    public void show() {
        if (rootOverlay.getVisibility() != View.VISIBLE) {
            rootOverlay.setVisibility(View.VISIBLE);
            // 材质化进入：淡入 + 轻微放大
            boolean reduce = com.olsc.manorbrowser.utils.Motion.isReduceMotion(activity);
            rootOverlay.setAlpha(0f);
            rootOverlay.setScaleX(reduce ? 1f : 0.985f);
            rootOverlay.setScaleY(reduce ? 1f : 0.985f);
            rootOverlay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                    activity, com.olsc.manorbrowser.utils.Motion.DURATION_SHEET))
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .start();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).setBarsVisible(false);
            }
            // 每次进入应用清空服务器上下文
            networkExecutor.execute(() -> {
                String baseUrl = aiClient.getServerUrl();
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    try {
                        URL url = new URL(baseUrl + "/api/chat/clear");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.getResponseCode(); // 发起请求
                    } catch (Exception e) {}
                }
            });
            if (chatAdapter.getItemCount() == 0) {
                chatAdapter.addMessage(AiChatAdapter.ChatMessage.aiMsg(activity.getString(R.string.ai_msg_hello)));
            }
        }
        updateStatus();
    }



    /**
     * 隐藏 AI 覆盖层（淡出，对称路径）
     */
    public void hide() {
        if (rootOverlay.getVisibility() == View.VISIBLE) {
            rootOverlay.animate()
                .alpha(0f)
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                    activity, com.olsc.manorbrowser.utils.Motion.DURATION_SHEET))
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .withEndAction(() -> {
                    // 若动画期间被新的 show() 打断（alpha 已拉高），不隐藏
                    if (rootOverlay.getAlpha() >= 0.5f) return;
                    rootOverlay.setVisibility(View.GONE);
                    rootOverlay.setAlpha(1f);
                    rootOverlay.setScaleX(1f);
                    rootOverlay.setScaleY(1f);
                })
                .start();
        } else {
            rootOverlay.setVisibility(View.GONE);
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setBarsVisible(true);
        }
    }

    /**
     * 更新 AI 状态及当前页面标题
     */
    public void updateStatus() {
        boolean running = aiClient.isRunning();
        
        mainHandler.post(() -> {
            if (running) {
                aiStatusDot.setBackgroundResource(R.drawable.shape_ai_status_dot);
            } else {
                // 离线状态
                aiStatusDot.setBackgroundColor(android.graphics.Color.GRAY);
            }

            try {
                String title = handler.getCurrentTitle();
                if (title != null && !title.isEmpty()) {
                    tvCurrentPage.setText(activity.getString(R.string.ai_monitoring_with_title, title));
                } else {
                    tvCurrentPage.setText(activity.getString(R.string.ai_monitoring_current_page));
                }
            } catch (Exception e) {}
        });
    }

    /**
     * 发送消息给 AI
     */
    private void sendMessage() {
        if (isRequesting) return;
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        etInput.setText("");
        isRequesting = true;
        btnSend.setImageResource(R.drawable.ic_stop);

        // 添加用户消息
        chatAdapter.addMessage(AiChatAdapter.ChatMessage.userMsg(text));
        
        // 添加待处理的 AI 消息
        chatAdapter.addMessage(AiChatAdapter.ChatMessage.aiThinking(activity.getString(R.string.ai_msg_sending_request)));
        rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        thinkingBar.setVisibility(View.VISIBLE);
        thinkingBar.setAlpha(0f);
        thinkingBar.setTranslationY(com.olsc.manorbrowser.utils.Motion.isReduceMotion(activity) ? 0f : dp6(activity));
        thinkingBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                activity, com.olsc.manorbrowser.utils.Motion.DURATION_SMALL))
            .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
            .start();
        tvThinkingBarText.setText(activity.getString(R.string.ai_thinking));

        String baseUrl = aiClient.getServerUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_not_connected));
            finishRequest();
            return;
        }

        networkExecutor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/api/chat");
                currentConn = (HttpURLConnection) url.openConnection();
                HttpURLConnection conn = currentConn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000); // 长时间等待事件流 (EventStream)
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject payload = new JSONObject();
                payload.put("message", text);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                mainHandler.post(() -> chatAdapter.updateLastAiStatus(activity.getString(R.string.ai_msg_waiting_reply)));

                int code = conn.getResponseCode();
                if (code != 200) {
                    mainHandler.post(() -> chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_request_failed, code)));
                    finishRequest();
                    return;
                }

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;

                StringBuilder currentAiText = new StringBuilder();
                final boolean[] isThinkingMode = {false};
                final boolean[] hasStartedThinkTag = {false};

                while (isRequesting && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (line.startsWith("data: ")) {
                        String dataStr = line.substring(6).trim();
                        if (dataStr.equals("[DONE]")) break;
                        try {
                            JSONObject data = new JSONObject(dataStr);
                            String type = data.optString("type");

                            mainHandler.post(() -> {
                                if (!isRequesting) return; // 如果已停止，不再接收
                                if ("thinking".equals(type)) {
                                    String c = data.optString("content");
                                    if (c != null && !c.isEmpty()) {
                                        if (!hasStartedThinkTag[0]) {
                                            chatAdapter.appendToLastAiMessage("<think>");
                                            hasStartedThinkTag[0] = true;
                                            isThinkingMode[0] = true;
                                        }
                                        chatAdapter.appendToLastAiMessage(c);
                                    }
                                } 
                                else if ("content".equals(type)) {
                                    String c = data.optString("content");
                                    if (c != null && !c.isEmpty()) {
                                        if (isThinkingMode[0]) {
                                            chatAdapter.appendToLastAiMessage("</think>\n");
                                            isThinkingMode[0] = false;
                                        }
                                        chatAdapter.appendToLastAiMessage(c);
                                    }
                                } 
                                else if ("tool_start".equals(type)) {
                                    String toolName = data.optString("name");
                                    JSONObject toolArgs = data.optJSONObject("args");
                                    String toolLabel = getFriendlyToolName(toolName, toolArgs);
                                    chatAdapter.addAiLogStep(toolLabel);
                                    tvThinkingBarText.setText(activity.getString(R.string.ai_msg_running_tool, toolLabel));
                                } 
                                else if ("tool_result".equals(type)) {
                                    chatAdapter.clearLastAiToolCall();
                                    tvThinkingBarText.setText(activity.getString(R.string.ai_msg_received_data));
                                    chatAdapter.updateLastAiStatus(activity.getString(R.string.ai_msg_processing_result));
                                } 
                                else if ("error".equals(type)) {
                                    chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_error, data.optString("message")));
                                } 
                                else if ("done".equals(type)) {
                                    chatAdapter.finalizeLastAiMessage();
                                }
                                LinearLayoutManager lm = (LinearLayoutManager) rvChat.getLayoutManager();
                                if (lm != null && lm.findLastVisibleItemPosition() >= chatAdapter.getItemCount() - 2) {
                                    rvChat.post(() -> rvChat.scrollBy(0, 50000));
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "SSE 解析 JSON 失败: " + dataStr, e);
                        }
                    }
                }
                reader.close();

            } catch (Exception e) {
                Log.e(TAG, "SSE 通信错误", e);
                mainHandler.post(() -> {
                    if (isRequesting) {
                        chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_comm_error, e.getMessage()));
                    }
                });
            }

            finishRequest();
        });
    }

    /**
     * 完成请求后的清理工作
     */
    private void finishRequest() {
        mainHandler.post(() -> {
            isRequesting = false;
            currentConn = null;
            btnSend.setEnabled(true);
            btnSend.setImageResource(R.drawable.ic_search);
            thinkingBar.animate()
                .alpha(0f)
                .translationY(dp6(activity))
                .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                    activity, com.olsc.manorbrowser.utils.Motion.DURATION_SMALL))
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .withEndAction(() -> {
                    // 若动画期间发起了新请求（isRequesting 已恢复 true），不隐藏思考条
                    if (isRequesting) return;
                    thinkingBar.setVisibility(View.GONE);
                    thinkingBar.setAlpha(1f);
                    thinkingBar.setTranslationY(0f);
                })
                .start();
            chatAdapter.finalizeLastAiMessage();
        });
    }

    /**
     * 停止当前 AI 任务
     */
    private void stopTask() {
        if (!isRequesting) return;
        
        // 1. 立即停止 UI 状态，设为 false
        isRequesting = false;
        btnSend.setEnabled(false); // 暂时禁用，防止连点
        chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_stopped));

        networkExecutor.execute(() -> {
            // 2. 断开当前的 SSE 连接 (此举会打断 readLine 调用)
            if (currentConn != null) {
                try {
                    currentConn.disconnect();
                } catch (Exception e) {}
                currentConn = null;
            }

            // 3. 通知服务端强制停止任务
            String baseUrl = aiClient.getServerUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                try {
                    URL stopUrl = new URL(baseUrl + "/api/chat/stop");
                    HttpURLConnection conn = (HttpURLConnection) stopUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.getResponseCode();
                } catch (Exception e) {
                    Log.w(TAG, "通知服务端停止失败: " + e.getMessage());
                }
            }

            // 4. 完成后清理并还原图标
            mainHandler.post(this::finishRequest);
        });
    }

    /**
     * 将技术化的工具名和参数转换为友好的多国语言描述
     */
    private String getFriendlyToolName(String toolName, JSONObject args) {
        if (toolName == null) return "Unknown";
        if (args == null) args = new JSONObject();
        
        switch (toolName) {
            case "browser_navigate": 
                return activity.getString(R.string.ai_tool_navigate, args.optString("url", "..."));
            case "browser_search": 
                return activity.getString(R.string.ai_tool_search, args.optString("keyword", "..."));
            case "browser_get_page_content": return activity.getString(R.string.ai_tool_get_content);
            case "browser_get_elements_tree": return activity.getString(R.string.ai_tool_get_tree);
            case "browser_click": 
                String sel = args.optString("selector");
                String txt = args.optString("text");
                String displayName = (txt != null && !txt.isEmpty() ? txt : (sel != null && !sel.isEmpty() ? sel : activity.getString(R.string.ai_tool_click_default)));
                return activity.getString(R.string.ai_tool_click, displayName);
            case "browser_set_input": 
                return activity.getString(R.string.ai_tool_set_input, args.optString("value", "***"));
            case "browser_scroll": 
                String dir = args.optString("direction", "down");
                String dirName = "down".equals(dir) ? activity.getString(R.string.ai_tool_scroll_down) : activity.getString(R.string.ai_tool_scroll_up);
                return activity.getString(R.string.ai_tool_scroll, dirName);
            case "browser_tab_management": {
                String act = args.optString("action");
                if ("new".equals(act)) return activity.getString(R.string.ai_tool_tab_new, args.optString("url", "..."));
                if ("switch".equals(act)) return activity.getString(R.string.ai_tool_tab_switch, args.optInt("index"));
                if ("close".equals(act)) return activity.getString(R.string.ai_tool_tab_close, args.optInt("index"));
                return activity.getString(R.string.ai_tool_tab_manage);
            }
            case "browser_navigate_control": {
                String act = args.optString("action");
                if ("back".equals(act)) return activity.getString(R.string.ai_tool_nav_back);
                if ("forward".equals(act)) return activity.getString(R.string.ai_tool_nav_forward);
                return activity.getString(R.string.ai_tool_nav_reload);
            }
            case "browser_get_status": return activity.getString(R.string.ai_tool_get_status);
            case "browser_get_history": return activity.getString(R.string.ai_tool_get_history);
            case "browser_get_downloads": return activity.getString(R.string.ai_tool_get_downloads);
            case "browser_clear_history": return activity.getString(R.string.ai_tool_clear_history);
            case "browser_clear_downloads": return activity.getString(R.string.ai_tool_clear_downloads);
            case "browser_exit_ai": return activity.getString(R.string.ai_tool_exit_ai);
            case "browser_eval_js": return activity.getString(R.string.ai_tool_eval_js);
            default: return toolName;
        }
    }
}

