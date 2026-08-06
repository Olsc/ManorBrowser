package com.olsc.manorbrowser.utils;

import android.animation.TimeInterpolator;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.view.animation.PathInterpolator;

/**
 * 统一动效规范（参考 Emil Kowalski 动画哲学与 Apple 流体设计）：
 * - 强 ease-out 曲线，替代内置弱曲线；UI 动画一律 ease-out（绝不 ease-in）
 * - 时长规范：按压反馈 120ms、小元素 160ms、切换 220ms、弹层 300ms，UI 动画均 < 400ms
 * - 只动画 transform / alpha（GPU 合成），避免 layout 属性
 * - 尊重系统"移除动画/减弱动画"设置（ANIMATOR_DURATION_SCALE == 0 时禁用位移类动画）
 */
public final class Motion {

    private Motion() {}

    /** 强 ease-out：cubic-bezier(0.23, 1, 0.32, 1)，进入/退出元素的首选 */
    public static final TimeInterpolator EASE_OUT =
        new PathInterpolator(0.23f, 1f, 0.32f, 1f);

    // ---- 时长规范（毫秒）----
    public static final int DURATION_PRESS = 120;     // 按压反馈
    public static final int DURATION_SMALL = 160;     // 小元素 / 徽标
    public static final int DURATION_SWITCH = 220;    // 页面/状态切换
    public static final int DURATION_SHEET = 300;     // 弹层 / 抽屉
    public static final int DURATION_ACTIVITY = 220;  // Activity 转场

    /** 系统是否开启了"移除动画"（开发者选项，动画缩放为 0） */
    public static boolean isReduceMotion(Context context) {
        try {
            ContentResolver cr = context.getContentResolver();
            float scale = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    /** 按系统减弱动画设置缩放的时长；reduce 时压到最小（元素仍有反馈但不位移） */
    public static int scaledDuration(Context context, int duration) {
        if (isReduceMotion(context)) return Math.min(duration, DURATION_PRESS);
        return duration;
    }
}
