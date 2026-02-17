package com.olsc.manorbrowser.adapter;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.TabInfo;

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

    public interface OnTabActionListener {
        void onTabClick(int position);
        void onTabClose(int position);
        void onTabLongPress(int position);
    }

    public TabSwitcherAdapter(List<TabInfo> tabs, OnTabActionListener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab_card, parent, false);

        android.util.DisplayMetrics metrics = parent.getContext().getResources().getDisplayMetrics();
        float screenRatio = (float) metrics.widthPixels / metrics.heightPixels;

        float density = metrics.density;
        int parentHeight = parent.getHeight();
        if (parentHeight <= 0) {
            parentHeight = metrics.heightPixels;
        }
        int cardHeight = parentHeight - (int) (64 * density);
        int targetWidth = (int) (cardHeight * screenRatio);

        int maxWidth = (int) (metrics.widthPixels * 0.85f);
        if (targetWidth > maxWidth) {
            targetWidth = maxWidth;
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
        holder.bind(tab, position, listener);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

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
            String titleText = tab.title;
            if (titleText == null || titleText.isEmpty() || titleText.equals("New Tab") || titleText.equals("新标签页")) {
                titleText = itemView.getContext().getString(R.string.title_new_tab);
            }
            title.setText(titleText);

            String urlText = tab.url;
            if (urlText != null && !urlText.isEmpty() && !urlText.equals("about:blank")) {
                url.setText(urlText);
                url.setVisibility(View.VISIBLE);
            } else {
                url.setVisibility(View.GONE);
            }

            if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
                preview.setImageBitmap(tab.thumbnail);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setBackgroundColor(0xFFE0E0E0);
            } else {
                preview.setImageResource(0);
                preview.setBackgroundColor(0xFFF5F5F5);
            }

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