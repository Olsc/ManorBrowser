package com.olsc.manorbrowser.adapter;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.TabInfo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TabAdapter extends RecyclerView.Adapter<TabAdapter.TabViewHolder> {

    private final List<TabInfo> tabs;
    private final OnTabClickListener listener;

    public interface OnTabClickListener {
        void onTabClick(int position);
        void onTabClose(int position);
    }

    public TabAdapter(List<TabInfo> tabs, OnTabClickListener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab, parent, false);
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
        TextView title;
        TextView url;
        ImageButton closeButton;
        ImageView preview;

        public TabViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tab_title);
            url = itemView.findViewById(R.id.tab_url);

            preview = itemView.findViewById(R.id.tab_preview);
        }

        public void bind(TabInfo tab, int position, OnTabClickListener listener) {
            title.setText(tab.title != null && !tab.title.isEmpty() ? tab.title : itemView.getContext().getString(R.string.title_new_tab));
            url.setText(tab.url != null ? tab.url : "");

            if (tab.thumbnail != null) {
                preview.setImageBitmap(tab.thumbnail);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                preview.setImageResource(android.R.drawable.ic_menu_gallery);
                preview.setScaleType(ImageView.ScaleType.CENTER);
            }

            itemView.setOnClickListener(v -> listener.onTabClick(position));

        }
    }
}
