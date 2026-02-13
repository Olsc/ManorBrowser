package com.olsc.manorbrowser.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.reflect.Field;

public class SwipeCallback extends ItemTouchHelper.Callback {

    public interface OnSwipeListener {
        void onSwiped(int position);
    }

    private final OnSwipeListener listener;

    public SwipeCallback(OnSwipeListener listener) {
        this.listener = listener;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {

        int dragFlags = 0;
        int swipeFlags = ItemTouchHelper.UP;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        if (direction == ItemTouchHelper.UP) {
            listener.onSwiped(viewHolder.getAdapterPosition());
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dY < 0) {

            float alpha = 1.0f - Math.abs(dY) / (float) viewHolder.itemView.getHeight();
            viewHolder.itemView.setAlpha(alpha);
            float scale = 1.0f - (Math.abs(dY) / (float) viewHolder.itemView.getHeight()) * 0.5f;
            viewHolder.itemView.setScaleX(scale);
            viewHolder.itemView.setScaleY(scale);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
