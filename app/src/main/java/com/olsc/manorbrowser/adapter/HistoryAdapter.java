package com.olsc.manorbrowser.adapter;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.HistoryStorage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<HistoryStorage.HistoryItem> historyList;
    private final OnItemClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public interface OnItemClickListener {
        void onItemClick(HistoryStorage.HistoryItem item);
    }

    public HistoryAdapter(List<HistoryStorage.HistoryItem> historyList, OnItemClickListener listener) {
        this.historyList = historyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryStorage.HistoryItem item = historyList.get(position);
        holder.bind(item, listener, sdf);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvUrl;
        TextView tvTime;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_history_title);
            tvUrl = itemView.findViewById(R.id.tv_history_url);
            tvTime = itemView.findViewById(R.id.tv_history_time);
        }

        public void bind(HistoryStorage.HistoryItem item, OnItemClickListener listener, SimpleDateFormat sdf) {
            String titleStr = (item.title != null && !item.title.isEmpty()) ? item.title : item.url;
            tvTitle.setText(titleStr);
            tvUrl.setText(item.url);
            tvTime.setText(sdf.format(new Date(item.timestamp)));

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}

