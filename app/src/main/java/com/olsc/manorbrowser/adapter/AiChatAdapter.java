package com.olsc.manorbrowser.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.olsc.manorbrowser.R;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话气泡 Adapter
 * 支持：用户消息、AI消息、AI思考中状态、Tool调用提示
 */
public class AiChatAdapter extends RecyclerView.Adapter<AiChatAdapter.VH> {

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI   = 1;

    public static class ChatMessage {
        public int type;          // TYPE_USER / TYPE_AI
        public String text;       // 消息文本
        public String toolCall;   // tool调用描述（可选）
        public boolean thinking;  // 是否正在思考
        public String statusText; // 思考状态文字（如"执行工具中..."）
        public final List<String> logSteps = new ArrayList<>(); // 操作日志记录

        public ChatMessage(int type, String text) {
            this.type = type;
            this.text = text;
        }

        public static ChatMessage userMsg(String text) {
            return new ChatMessage(TYPE_USER, text);
        }

        public static ChatMessage aiMsg(String text) {
            return new ChatMessage(TYPE_AI, text);
        }

        public static ChatMessage aiThinking(String statusText) {
            ChatMessage m = new ChatMessage(TYPE_AI, "");
            m.thinking = true;
            m.statusText = statusText != null ? statusText : "思考中...";
            return m;
        }
    }

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastAiMessage(String newText) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).type == TYPE_AI) {
                messages.get(i).text = newText;
                messages.get(i).thinking = false;
                notifyItemChanged(i, "text");
                return;
            }
        }
    }

    public void appendToLastAiMessage(String chunk) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.type == TYPE_AI) {
                m.text = m.text + chunk;
                m.thinking = false;
                notifyItemChanged(i, "text");
                return;
            }
        }
    }

    public void updateLastAiStatus(String statusText) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.type == TYPE_AI) {
                m.thinking = true;
                m.statusText = statusText;
                notifyItemChanged(i, "status");
                return;
            }
        }
    }

    public void addAiLogStep(String step) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.type == TYPE_AI) {
                m.logSteps.add(step);
                notifyItemChanged(i, "tool");
                return;
            }
        }
    }

    public void clearLastAiToolCall() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.type == TYPE_AI) {
                m.toolCall = null;
                // m.logSteps.clear(); // 通常不清除历史日志，让用户看到轨迹
                notifyItemChanged(i, "tool");
                return;
            }
        }
    }

    public void finalizeLastAiMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.type == TYPE_AI) {
                m.thinking = false;
                notifyItemChanged(i, "status");
                return;
            }
        }
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_chat_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        onBindViewHolder(h, pos, new ArrayList<>());
    }

    private CharSequence formatMessage(String text) {
        if (text == null) return "";
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
        String lowerText = text.toLowerCase();
        String thinkStart = "<think>";
        String thinkEnd = "</think>";
        
        int currentIndex = 0;
        while (currentIndex < text.length()) {
            int startIdx = lowerText.indexOf(thinkStart, currentIndex);
            if (startIdx == -1) {
                ssb.append(text.substring(currentIndex));
                break;
            }
            
            ssb.append(text.substring(currentIndex, startIdx));
            
            int thinkContentStart = startIdx + thinkStart.length();
            if (thinkContentStart < text.length() && text.charAt(thinkContentStart) == '\n') {
                thinkContentStart++;
            }
            
            int endIdx = lowerText.indexOf(thinkEnd, thinkContentStart);
            int spanStart = ssb.length();
            
            if (endIdx == -1) {
                ssb.append(text.substring(thinkContentStart));
                int spanEnd = ssb.length();
                setThinkSpan(ssb, spanStart, spanEnd);
                break;
            } else {
                ssb.append(text.substring(thinkContentStart, endIdx));
                int spanEnd = ssb.length();
                setThinkSpan(ssb, spanStart, spanEnd);
                
                currentIndex = endIdx + thinkEnd.length();
                if (currentIndex < text.length() && text.charAt(currentIndex) == '\n') {
                    currentIndex++;
                }
            }
        }
        return ssb;
    }

    private void setThinkSpan(android.text.SpannableStringBuilder ssb, int start, int end) {
        if (start >= end) return;
        ssb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        ChatMessage msg = messages.get(pos);

        if (!payloads.isEmpty()) {
            // 局部更新逻辑，避免闪烁
            for (Object payload : payloads) {
                if ("text".equals(payload)) {
                    if (msg.text != null && !msg.text.isEmpty()) {
                        h.tvAiBubble.setVisibility(View.VISIBLE);
                        h.tvAiBubble.setText(formatMessage(msg.text));
                    } else {
                        h.tvAiBubble.setVisibility(View.GONE);
                    }
                    // 状态通常在更新 text 时也会改变
                    h.aiStatusRow.setVisibility(msg.thinking ? View.VISIBLE : View.GONE);
                } else if ("status".equals(payload)) {
                    h.aiStatusRow.setVisibility(msg.thinking ? View.VISIBLE : View.GONE);
                    h.tvAiStatusText.setText(msg.statusText != null ? msg.statusText : "思考中...");
                } else if ("tool".equals(payload)) {
                    StringBuilder sb = new StringBuilder();
                    if (msg.toolCall != null && !msg.toolCall.isEmpty()) {
                        sb.append(msg.toolCall).append("\n");
                    }
                    for (String step : msg.logSteps) {
                        sb.append("- ").append(step).append("\n");
                    }
                    String text = sb.toString().trim();
                    if (!text.isEmpty()) {
                        h.toolCallContainer.setVisibility(View.VISIBLE);
                        h.tvToolCallText.setText(text);
                    } else {
                        h.toolCallContainer.setVisibility(View.GONE);
                    }
                }
            }
            return;
        }

        // 全局绑定逻辑
        if (msg.type == TYPE_USER) {
            h.rowUser.setVisibility(View.VISIBLE);
            h.rowAi.setVisibility(View.GONE);
            h.tvUserBubble.setText(msg.text);
        } else {
            h.rowUser.setVisibility(View.GONE);
            h.rowAi.setVisibility(View.VISIBLE);

            // 思考状态
            if (msg.thinking) {
                h.aiStatusRow.setVisibility(View.VISIBLE);
                h.tvAiStatusText.setText(msg.statusText != null ? msg.statusText : "思考中...");
            } else {
                h.aiStatusRow.setVisibility(View.GONE);
            }

            // Tool调用轨迹
            StringBuilder sb = new StringBuilder();
            if (msg.toolCall != null && !msg.toolCall.isEmpty()) {
                sb.append(msg.toolCall).append("\n");
            }
            for (String step : msg.logSteps) {
                sb.append("- ").append(step).append("\n");
            }
            String logText = sb.toString().trim();
            if (!logText.isEmpty()) {
                h.toolCallContainer.setVisibility(View.VISIBLE);
                h.tvToolCallText.setText(logText);
            } else {
                h.toolCallContainer.setVisibility(View.GONE);
            }

            // 内容气泡
            if (msg.text != null && !msg.text.isEmpty()) {
                h.tvAiBubble.setVisibility(View.VISIBLE);
                h.tvAiBubble.setText(formatMessage(msg.text));
            } else {
                h.tvAiBubble.setVisibility(View.GONE);
            }
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout rowAi, rowUser;
        LinearLayout aiStatusRow, toolCallContainer;
        TextView tvAiStatusText, tvToolCallText, tvAiBubble, tvUserBubble;

        VH(View v) {
            super(v);
            rowAi = v.findViewById(R.id.row_ai);
            rowUser = v.findViewById(R.id.row_user);
            aiStatusRow = v.findViewById(R.id.ai_status_row);
            toolCallContainer = v.findViewById(R.id.tool_call_container);
            tvAiStatusText = v.findViewById(R.id.tv_ai_status_text);
            tvToolCallText = v.findViewById(R.id.tv_tool_call_text);
            tvAiBubble = v.findViewById(R.id.tv_ai_bubble);
            tvUserBubble = v.findViewById(R.id.tv_user_bubble);
        }
    }
}
