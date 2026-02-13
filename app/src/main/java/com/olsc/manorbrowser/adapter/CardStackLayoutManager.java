package com.olsc.manorbrowser.adapter;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class CardStackLayoutManager extends LinearLayoutManager {

    private static final float SCALE_FACTOR = 0.2f;
    private static final float TRANS_FACTOR = 0.55f;

    private final LinearSmoothScroller smoothScroller;

    public CardStackLayoutManager(Context context) {
        super(context, HORIZONTAL, false);

        smoothScroller = new LinearSmoothScroller(context) {
            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {

                return 150f / displayMetrics.densityDpi;
            }
        };
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        smoothScroller.setTargetPosition(position);
        startSmoothScroll(smoothScroller);
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int scrolled = super.scrollHorizontallyBy(dx, recycler, state);
        updateChildTransforms();
        return scrolled;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        super.onLayoutChildren(recycler, state);
        updateChildTransforms();
    }

    public void updateChildTransforms() {
        int childCount = getChildCount();
        if (childCount == 0) return;

        int width = getWidth();
        int centerX = width / 2;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == null) continue;

            float childCenterX = (getDecoratedLeft(child) + getDecoratedRight(child)) / 2f;

            float distanceFromCenter = (centerX - childCenterX) / width;
            float absDistance = Math.abs(distanceFromCenter);

            float scale = 1.0f - Math.min(absDistance * SCALE_FACTOR, 0.2f);
            child.setScaleX(scale);
            child.setScaleY(scale);

            float translationX = 0f;
            if (distanceFromCenter > 0) {
                 translationX = (child.getWidth() * (1 - scale)) / 2f + (distanceFromCenter * child.getWidth() * 0.15f);
            } else {
                 translationX = -(child.getWidth() * (1 - scale)) / 2f + (distanceFromCenter * child.getWidth() * 0.15f);
            }
            child.setTranslationX(translationX);

            child.setTranslationZ(100f * (1.0f - absDistance));

            float alpha = 1.0f - Math.min(absDistance * 0.6f, 0.5f);
            child.setAlpha(alpha);

            child.setRotationY(0f);
        }
    }
}
