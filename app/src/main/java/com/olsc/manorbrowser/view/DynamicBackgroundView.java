package com.olsc.manorbrowser.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class DynamicBackgroundView extends FrameLayout {

    public enum EffectMode {
        METEOR, RAIN, SNOW, AURORA, SAKURA, SOLID
    }

    private EffectMode currentMode = EffectMode.METEOR;

    public DynamicBackgroundView(Context context) {
        this(context, null);
    }

    public DynamicBackgroundView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DynamicBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setMode(currentMode);
    }

    public void setMode(EffectMode mode) {
        this.currentMode = mode;
        removeAllViews();

        Context context = getContext();
        switch (mode) {
            case RAIN:
                addView(new RainView(context));
                break;
            case SNOW:
                addView(new SnowView(context));
                break;
            case AURORA:
                addView(new AuroraView(context));
                break;
            case SAKURA:
                addView(new SakuraView(context));
                break;
            case SOLID:
                android.view.View solidView = new android.view.View(context);
                solidView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                addView(solidView);
                break;
            case METEOR:
            default:
                addView(new MeteorView(context));
                break;
        }
    }
}

