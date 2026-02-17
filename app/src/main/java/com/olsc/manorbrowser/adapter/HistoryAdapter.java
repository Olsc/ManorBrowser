package com.olsc.manorbrowser.adapter;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.HistoryStorage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<HistoryStorage.HistoryItem> historyList;
    private final OnItemClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private boolean selectionMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();

    public interface OnItemClickListener {
        void onItemClick(HistoryStorage.HistoryItem item);
        void onItemLongClick(HistoryStorage.HistoryItem item, int position);
        void onSelectionChanged(int count);
    }

    public HistoryAdapter(List<HistoryStorage.HistoryItem> historyList, OnItemClickListener listener) {
        this.historyList = historyList;
        this.listener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        if (!selectionMode) {
            selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        listener.onSelectionChanged(selectedPositions.size());
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < historyList.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedPositions.size());
    }

    public Set<Integer> getSelectedPositions() {
        return new HashSet<>(selectedPositions);
    }

    public int getSelectedCount() {
        return selectedPositions.size();
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
        boolean isSelected = selectedPositions.contains(position);
        holder.bind(item, listener, sdf, position, selectionMode, isSelected);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvUrl;
        TextView tvTime;
        CheckBox checkbox;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_history_title);
            tvUrl = itemView.findViewById(R.id.tv_history_url);
            tvTime = itemView.findViewById(R.id.tv_history_time);
            checkbox = itemView.findViewById(R.id.checkbox_select);
        }

        public void bind(HistoryStorage.HistoryItem item, OnItemClickListener listener, 
                        SimpleDateFormat sdf, int position, boolean selectionMode, boolean isSelected) {
            String titleStr = (item.title != null && !item.title.isEmpty()) ? item.title : item.url;
            tvTitle.setText(titleStr);
            tvUrl.setText(item.url);
            tvTime.setText(sdf.format(new Date(item.timestamp)));

            checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            checkbox.setChecked(isSelected);

            itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(position);
                } else {
                    listener.onItemClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (!selectionMode) {
                    listener.onItemLongClick(item, position);
                    return true;
                }
                return false;
            });
        }
    }
}

