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
        public int type;          // 消息类型: 用户(TYPE_USER) / AI(TYPE_AI)
        public String text;       // 消息文本
        public String toolCall;   // 工具调用描述（可选）
        public boolean thinking;  // 是否正在思考
        public final List<Boolean> thinkExpandedStates = new ArrayList<>(); // 记录每一段思考的展开状态
        public String statusText; // 状态文字
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
            m.statusText = statusText;
            return m;
        }
    }

    private final List<ChatMessage> messages = new ArrayList<>();

    /** 正在播放的思考气泡展开/收起动画（快速连点先取消旧的） */
    private android.animation.ValueAnimator thinkAnim = null;

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

    private void bindAiText(VH h, ChatMessage msg, int pos) {
        String plainText = msg.text;
        if (plainText != null) {
            plainText = plainText.replaceAll("(?s)<tool_call>.*?(</tool_call>|$)", "");
        } else {
            plainText = "";
        }

        h.thinkContainer.removeAllViews();
        h.thinkContainer.setVisibility(View.GONE);

        String lowerText = plainText.toLowerCase();
        int lastPos = 0;
        int thinkIndex = 0;
        StringBuilder normalTextBuilder = new StringBuilder();

        while (true) {
            int startIdx = lowerText.indexOf("<think>", lastPos);
            if (startIdx == -1) {
                normalTextBuilder.append(plainText.substring(lastPos));
                break;
            }

            normalTextBuilder.append(plainText.substring(lastPos, startIdx));
            int contentStart = startIdx + 7;
            int endIdx = lowerText.indexOf("</think>", contentStart);

            String thinkContent;
            if (endIdx != -1) {
                thinkContent = plainText.substring(contentStart, endIdx).trim();
                lastPos = endIdx + 8;
            } else {
                thinkContent = plainText.substring(contentStart).trim();
                lastPos = plainText.length();
            }

            if (!thinkContent.isEmpty()) {
                addThinkView(h.thinkContainer, msg, pos, thinkIndex++, thinkContent);
                h.thinkContainer.setVisibility(View.VISIBLE);
            }
        }

        String finalNormal = normalTextBuilder.toString().trim();
        if (!finalNormal.isEmpty()) {
            h.tvAiBubble.setVisibility(View.VISIBLE);
            h.tvAiBubble.setText(finalNormal);
        } else {
            h.tvAiBubble.setVisibility(View.GONE);
        }
    }

    private void addThinkView(LinearLayout container, ChatMessage msg, int messagePos, int thinkIndex, String content) {
        View v = LayoutInflater.from(container.getContext()).inflate(R.layout.view_think_bubble, container, false);
        TextView tv = v.findViewById(R.id.tv_think_content);
        
        while (msg.thinkExpandedStates.size() <= thinkIndex) {
            msg.thinkExpandedStates.add(false);
        }
        boolean isExpanded = msg.thinkExpandedStates.get(thinkIndex);

        tv.setText(content);
        if (!isExpanded) {
            tv.setMaxLines(2000);
            int lineHeight = tv.getLineHeight();
            if (lineHeight <= 0) lineHeight = (int) (tv.getTextSize() * 1.4f + 0.5f);
            tv.setMaxHeight(lineHeight * 3 + tv.getPaddingTop() + tv.getPaddingBottom());
            tv.setGravity(android.view.Gravity.BOTTOM);
        } else {
            tv.setMaxLines(Integer.MAX_VALUE);
            tv.setMaxHeight(Integer.MAX_VALUE);
            tv.setGravity(android.view.Gravity.TOP);
        }

        v.setOnClickListener(view -> {
            // 平滑展开/收起：动画 maxHeight 替代硬切
            // 点击时从状态列表读最新值，避免闭包捕获旧 isExpanded 导致无法收起
            boolean nowExpanded = msg.thinkExpandedStates.get(thinkIndex);
            boolean expanding = !nowExpanded;
            msg.thinkExpandedStates.set(thinkIndex, expanding);
            int lineHeight = tv.getLineHeight();
            if (lineHeight <= 0) lineHeight = (int) (tv.getTextSize() * 1.4f + 0.5f);
            final int collapsedMax = lineHeight * 3 + tv.getPaddingTop() + tv.getPaddingBottom();
            final int expandedMax = 100000; // 等价"不限制"
            // 快速连点时取消上一次动画，从当前高度继续
            if (thinkAnim != null && thinkAnim.isRunning()) {
                thinkAnim.cancel();
            }
            int start = tv.getMaxHeight();
            int end = expanding ? expandedMax : collapsedMax;
            // 收起时立即切底部对齐（显示末 3 行），展开时动画结束时切顶部对齐
            if (!expanding) {
                tv.setGravity(android.view.Gravity.BOTTOM);
            }
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(start, end);
            thinkAnim = anim;
            anim.setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                v.getContext(), com.olsc.manorbrowser.utils.Motion.DURATION_SWITCH));
            anim.setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT);
            anim.addUpdateListener(a -> {
                tv.setMaxHeight((Integer) a.getAnimatedValue());
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled = false;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                    if (thinkAnim == animation) thinkAnim = null;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (cancelled) return;
                    if (thinkAnim == animation) thinkAnim = null;
                    tv.setMaxLines(expanding ? Integer.MAX_VALUE : 2000);
                    tv.setMaxHeight(end);
                    tv.setGravity(expanding
                        ? android.view.Gravity.TOP
                        : android.view.Gravity.BOTTOM);
                    // 高度变化后请求重绘，避免内容残留
                    tv.postInvalidate();
                }
            });
            anim.start();
        });
        container.addView(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        ChatMessage msg = messages.get(pos);

        if (!payloads.isEmpty()) {
            // 局部更新逻辑，避免闪烁
            for (Object payload : payloads) {
                if ("text".equals(payload)) {
                    bindAiText(h, msg, pos);
                    // 状态通常在更新 text 时也会改变
                    h.aiStatusRow.setVisibility(msg.thinking ? View.VISIBLE : View.GONE);
                } else if ("status".equals(payload)) {
                    h.aiStatusRow.setVisibility(msg.thinking ? View.VISIBLE : View.GONE);
                    h.tvAiStatusText.setText(msg.statusText != null ? msg.statusText : h.itemView.getContext().getString(R.string.ai_thinking_dots));
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
                h.tvAiStatusText.setText(msg.statusText != null ? msg.statusText : h.itemView.getContext().getString(R.string.ai_thinking_dots));
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

            // 内容气泡 (普通文本与思考文本)
            bindAiText(h, msg, pos);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout rowAi, rowUser;
        LinearLayout aiStatusRow, toolCallContainer, thinkContainer;
        TextView tvAiStatusText, tvToolCallText, tvAiBubble, tvUserBubble;

        VH(View v) {
            super(v);
            rowAi = v.findViewById(R.id.row_ai);
            rowUser = v.findViewById(R.id.row_user);
            aiStatusRow = v.findViewById(R.id.ai_status_row);
            toolCallContainer = v.findViewById(R.id.tool_call_container);
            thinkContainer = v.findViewById(R.id.think_container);
            tvAiStatusText = v.findViewById(R.id.tv_ai_status_text);
            tvToolCallText = v.findViewById(R.id.tv_tool_call_text);
            tvAiBubble = v.findViewById(R.id.tv_ai_bubble);
            tvUserBubble = v.findViewById(R.id.tv_user_bubble);
        }
    }
}
