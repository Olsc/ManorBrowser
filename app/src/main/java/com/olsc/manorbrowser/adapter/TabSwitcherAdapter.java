/**
 * 标签切换器适配器
 * 用于在多标签切换界面展示每个标签页的预览卡片，包含标题、URL 以及缩略图预览。
 */
package com.olsc.manorbrowser.adapter;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.TabInfo;
import com.olsc.manorbrowser.data.TabStorage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TabSwitcherAdapter extends RecyclerView.Adapter<TabSwitcherAdapter.TabViewHolder> {
    
    private final List<TabInfo> tabs;
    private final OnTabActionListener listener;

    /**
     * 标签操作回调接口
     */
    public interface OnTabActionListener {
        void onTabClick(int position);
        void onTabClose(int position);
        void onTabLongPress(int position);
    }

    public TabSwitcherAdapter(List<TabInfo> tabs, OnTabActionListener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    /** 预设卡片宽度（像素）。由外部按容器实际尺寸计算后设置，避免首次布局时 getHeight()==0 导致宽高错误 */
    public void setCardWidth(int cardWidthPx) {
        this.cardWidthPx = cardWidthPx;
    }

    private int cardWidthPx = -1;

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab_card, parent, false);

        // --- 动态计算卡片尺寸 ---
        // 优先使用外部预设宽度（按容器实际尺寸计算，避免首次布局 getHeight()==0）
        int targetWidth = cardWidthPx;
        if (targetWidth <= 0) {
            // 兜底：按父容器当前高度与屏幕比例计算
            android.util.DisplayMetrics metrics = parent.getContext().getResources().getDisplayMetrics();
            float screenRatio = (float) metrics.widthPixels / metrics.heightPixels;
            float density = metrics.density;

            int parentHeight = parent.getHeight();
            if (parentHeight <= 0) {
                parentHeight = metrics.heightPixels;
            }
            int cardHeight = parentHeight - (int) (64 * density);
            targetWidth = (int) (cardHeight * screenRatio);
            int maxWidth = (int) (metrics.widthPixels * 0.85f);
            if (targetWidth > maxWidth) {
                targetWidth = maxWidth;
            }
        }

        View cardView = view.findViewById(R.id.tab_card_view);
        if (cardView != null) {
            ViewGroup.LayoutParams lp = cardView.getLayoutParams();
            lp.width = targetWidth;
            cardView.setLayoutParams(lp);
        }

        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        TabInfo tab = tabs.get(position);
        // 每次绑定同步卡片宽度（notifyDataSetChanged 不会重建已存在的 ViewHolder，
        // 旋转/尺寸变化后宽度更新必须在这里生效）
        if (cardWidthPx > 0) {
            View cardView = holder.itemView.findViewById(R.id.tab_card_view);
            if (cardView != null) {
                ViewGroup.LayoutParams lp = cardView.getLayoutParams();
                if (lp.width != cardWidthPx) {
                    lp.width = cardWidthPx;
                    cardView.setLayoutParams(lp);
                }
            }
        }
        holder.bind(tab, position, listener);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    /**
     * 标签预览项的 ViewHolder
     */
    static class TabViewHolder extends RecyclerView.ViewHolder {
        ImageView preview;
        TextView title;
        TextView url;

        public TabViewHolder(@NonNull View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.tab_preview);
            title = itemView.findViewById(R.id.tab_title);
            url = itemView.findViewById(R.id.tab_url);
        }

        public void bind(TabInfo tab, int position, OnTabActionListener listener) {
            // 设置标题，处理空标题或默认页情况
            String titleText = tab.title;
            if (titleText == null || titleText.isEmpty() || titleText.equals("New Tab") || titleText.equals("新标签页")) {
                titleText = itemView.getContext().getString(R.string.title_new_tab);
            }
            title.setText(titleText);

            // 设置 URL 预览
            String urlText = tab.url;
            if (urlText != null && !urlText.isEmpty() && !urlText.equals("about:blank")) {
                url.setText(urlText);
                url.setVisibility(View.VISIBLE);
            } else {
                url.setVisibility(View.GONE);
            }

            // 处理缩略图加载
            if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
                // 如果内存中已有截图，直接显示
                preview.setImageBitmap(tab.thumbnail);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.getContext(), com.olsc.manorbrowser.R.color.divider_color));
            } else {
                // 否则显示占位背景并异步从磁盘加载缓存的缩略图
                preview.setImageResource(0);
                preview.setBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.getContext(), com.olsc.manorbrowser.R.color.background_main));
                TabStorage.loadThumbnailAsync(itemView.getContext(), tab.id, bitmap -> {
                    if (bitmap != null && (tab.thumbnail == null || tab.thumbnail.isRecycled())) {
                        tab.thumbnail = bitmap;
                        // 确保加载完成时，ViewHolder 仍绑定在当前位置，防止列表滚动导致的错位
                        if (getBindingAdapterPosition() == position) {
                            preview.setImageBitmap(bitmap);
                            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            preview.setBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.getContext(), com.olsc.manorbrowser.R.color.divider_color));
                        }
                    }
                });
            }

            // 事件点击监听
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onTabClick(pos);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onTabLongPress(pos);
                    return true;
                }
                return false;
            });
        }
    }
}