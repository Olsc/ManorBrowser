package com.olsc.manorbrowser.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.BookmarkItem;
import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

    public interface OnBookmarkClickListener {
        void onBookmarkClick(BookmarkItem item);
        void onBookmarkLongClick(BookmarkItem item);
    }

    private List<BookmarkItem> list;
    private OnBookmarkClickListener listener;

    public BookmarkAdapter(List<BookmarkItem> list, OnBookmarkClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookmarkItem item = list.get(position);
        holder.tvTitle.setText(item.title);
        
        if (item.type == BookmarkItem.Type.FOLDER) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.tvUrl.setVisibility(View.GONE);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_bookmark);
            holder.tvUrl.setText(item.url);
            holder.tvUrl.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> listener.onBookmarkClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onBookmarkLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvUrl;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvUrl = itemView.findViewById(R.id.tv_url);
        }
    }
}
