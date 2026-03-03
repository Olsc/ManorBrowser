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
    private boolean isRequesting = false;

    public AiOverlayManager(Activity activity, View rootOverlay, AiCommandClient aiClient, BrowserCommandServer.CommandHandler handler) {
        this.activity = activity;
        this.rootOverlay = rootOverlay;
        this.aiClient = aiClient;
        this.handler = handler;

        initViews();
        setupListeners();
        networkExecutor = Executors.newSingleThreadExecutor();
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

        btnSend.setOnClickListener(v -> sendMessage());

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
     * 显示 AI 覆盖层
     */
    public void show() {
        if (rootOverlay.getVisibility() != View.VISIBLE) {
            rootOverlay.setVisibility(View.VISIBLE);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).setBarsVisible(false);
            }
            if (chatAdapter.getItemCount() == 0) {
                chatAdapter.addMessage(AiChatAdapter.ChatMessage.aiMsg(activity.getString(R.string.ai_msg_hello)));
            }
        }
        updateStatus();
    }



    /**
     * 隐藏 AI 覆盖层
     */
    public void hide() {
        rootOverlay.setVisibility(View.GONE);
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
        btnSend.setEnabled(false);

        // 添加用户消息
        chatAdapter.addMessage(AiChatAdapter.ChatMessage.userMsg(text));
        
        // 添加待处理的 AI 消息
        chatAdapter.addMessage(AiChatAdapter.ChatMessage.aiThinking(activity.getString(R.string.ai_msg_sending_request)));
        rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        thinkingBar.setVisibility(View.VISIBLE);
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
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (line.startsWith("data: ")) {
                        String dataStr = line.substring(6).trim();
                        if (dataStr.equals("[DONE]")) break;
                        try {
                            JSONObject data = new JSONObject(dataStr);
                            String type = data.optString("type");

                            mainHandler.post(() -> {
                                if ("thinking".equals(type)) {
                                    chatAdapter.updateLastAiStatus(data.optString("content", activity.getString(R.string.ai_msg_thinking)));
                                } 
                                else if ("content".equals(type)) {
                                    chatAdapter.updateLastAiStatus(null);
                                    String c = data.optString("content");
                                    if (c != null) {
                                        currentAiText.append(c);
                                        chatAdapter.updateLastAiMessage(currentAiText.toString());
                                    }
                                } 
                                else if ("tool_start".equals(type)) {
                                    String toolName = data.optString("name");
                                    chatAdapter.updateLastAiToolCall(activity.getString(R.string.ai_tool_call_label, toolName));
                                    tvThinkingBarText.setText(activity.getString(R.string.ai_msg_running_tool, toolName));
                                } 
                                else if ("tool_result".equals(type)) {
                                    chatAdapter.updateLastAiToolCall(null);
                                    tvThinkingBarText.setText(activity.getString(R.string.ai_msg_received_data));
                                    chatAdapter.updateLastAiStatus(activity.getString(R.string.ai_msg_processing_result));
                                } 
                                else if ("error".equals(type)) {
                                    chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_error, data.optString("message")));
                                } 
                                else if ("done".equals(type)) {
                                    chatAdapter.finalizeLastAiMessage();
                                }
                                rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "SSE 解析 JSON 失败: " + dataStr, e);
                        }
                    }
                }
                reader.close();

            } catch (Exception e) {
                Log.e(TAG, "SSE 通信错误", e);
                mainHandler.post(() -> chatAdapter.updateLastAiMessage(activity.getString(R.string.ai_msg_comm_error, e.getMessage())));
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
            btnSend.setEnabled(true);
            thinkingBar.setVisibility(View.GONE);
            chatAdapter.finalizeLastAiMessage();
        });
    }
}

