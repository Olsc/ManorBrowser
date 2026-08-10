/**
 * 标签页滑动删除的回调类。
 */
package com.olsc.manorbrowser.adapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
public class TabSwipeCallback extends ItemTouchHelper.SimpleCallback {
    public interface OnSwipeListener {
        void onSwiped(int position);
    }
    private final OnSwipeListener listener;    public TabSwipeCallback(OnSwipeListener listener) {
        super(0, ItemTouchHelper.UP);
        this.listener = listener;
    }
    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // 删除动画播放期间 position 可能变为 NO_POSITION，用最终布局位置
        int pos = viewHolder.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) {
            pos = viewHolder.getLayoutPosition();
        }
        if (pos != RecyclerView.NO_POSITION) {
            // 同步移除数据：ItemTouchHelper 会在 onSwiped 后自行衔接剩余卡片动画。
            // 不可 post 延迟——延迟期间用户可再滑其他卡，导致 position 错位成"幽灵卡"。
            listener.onSwiped(pos);
        }
    }
    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        View itemView = viewHolder.itemView;
        itemView.setAlpha(1.0f);
        itemView.setRotation(0f);
    }
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.25f;
    }
    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 2f;
    }
}
