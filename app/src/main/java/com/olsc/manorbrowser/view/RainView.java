package com.olsc.manorbrowser.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RainView extends View {

    private Paint bgPaint;
    private Paint rainPaint;
    private Paint glassDropPaint;
    private Paint mistPaint;
    
    private int width, height;
    private float density;
    private final Random random = new Random();

    private final List<RainLine> rainLines = new ArrayList<>();
    private final List<GlassDrop> glassDrops = new ArrayList<>();
    private final List<MistShape> mists = new ArrayList<>();

    public RainView(Context context) {
        this(context, null);
    }

    public RainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);

        rainPaint = new Paint();
        rainPaint.setColor(0x88AACCFF); // Light blue-ish grey
        rainPaint.setStyle(Paint.Style.STROKE);
        rainPaint.setStrokeCap(Paint.Cap.BUTT);
        rainPaint.setAntiAlias(true);

        glassDropPaint = new Paint();
        glassDropPaint.setStyle(Paint.Style.FILL);
        glassDropPaint.setColor(0xCCFFFFFF); // Semi-transparent white
        glassDropPaint.setAntiAlias(true);

        mistPaint = new Paint();
        mistPaint.setStyle(Paint.Style.FILL);
        mistPaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        LinearGradient bgGradient = new LinearGradient(
                0, 0, 0, height,
                new int[]{0xFF1A2A3A, 0xFF2C3E50, 0xFF4B6584}, 
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGradient);

        rainLines.clear();
        for (int i = 0; i < 150; i++) {
            rainLines.add(new RainLine(w, h, density));
        }

        glassDrops.clear();
        for (int i = 0; i < 30; i++) {
            glassDrops.add(new GlassDrop(w, h, density));
        }

        mists.clear();
        for (int i=0; i<3; i++) {
            mists.add(new MistShape(w, h, density));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, width, height, bgPaint);

        for (MistShape mist : mists) {
            mist.update();
            mistPaint.setColor(0x33B0BEC5); // Very faint blue-grey
            mistPaint.setAlpha(mist.alpha);
            canvas.drawCircle(mist.x, mist.y, mist.radius, mistPaint);
        }

        rainPaint.setStrokeWidth(1 * density);
        for (RainLine line : rainLines) {
            line.update();
            rainPaint.setAlpha(line.alpha);
            canvas.drawLine(line.x, line.y, line.x, line.y + line.length, rainPaint);
        }

        for (GlassDrop drop : glassDrops) {
            drop.update();
            
            glassDropPaint.setAlpha(drop.alpha);
            
            if (drop.speed > 0.1) {
                canvas.drawCircle(drop.x, drop.y - drop.size * 1.5f, drop.size * 0.5f, glassDropPaint);
                canvas.drawCircle(drop.x, drop.y, drop.size, glassDropPaint);
            } else {
                canvas.drawCircle(drop.x, drop.y, drop.size, glassDropPaint);
            }
            
            glassDropPaint.setAlpha(200);
            canvas.drawCircle(drop.x - drop.size*0.3f, drop.y - drop.size*0.3f, drop.size*0.25f, glassDropPaint);
        }

        postInvalidateOnAnimation();
    }

    private class RainLine {
        float x, y, length, speed;
        int alpha;

        RainLine(int w, int h, float density) {
            reset(w, h, density, true);
        }

        void reset(int w, int h, float density, boolean randomStart) {
            x = random.nextInt(w);
            y = randomStart ? random.nextInt(h) : -random.nextInt(200);
            length = (20 + random.nextInt(20)) * density;
            speed = (15 + random.nextFloat() * 10) * density;
            alpha = 50 + random.nextInt(100);
        }

        void update() {
            y += speed;
            if (y > height) {
                reset(width, height, density, false);
            }
        }
    }

    private class GlassDrop {
        float x, y, size, speed;
        int alpha;
        int state; // 0=Wait, 1=Slide

        GlassDrop(int w, int h, float density) {
            reset(w, h, density, true);
        }

        void reset(int w, int h, float density, boolean randomStart) {
            x = random.nextInt(w);
            y = randomStart ? random.nextInt(h) : -random.nextInt(100);
            size = (2 + random.nextFloat() * 3) * density;
            alpha = 150 + random.nextInt(80);
            state = 0;
            speed = 0;
        }

        void update() {
            if (state == 0) {
                if (random.nextInt(1000) < 10) {
                    state = 1; // Start sliding
                    speed = (0.5f + random.nextFloat()) * density;
                }
            } else {
                y += speed;
                if (random.nextFloat() < 0.1f) speed += 0.1f;
                if (random.nextInt(1000) < 5) {
                    state = 0;
                    speed = 0;
                }
            }

            if (y > height + 50) {
                reset(width, height, density, false);
            }
        }
    }

    private class MistShape {
        float x, y, radius, speed;
        int alpha;

        MistShape(int w, int h, float density) {
            reset(w, h, density);
        }

        void reset(int w, int h, float density) {
             x = random.nextInt(w);
             y = h * 0.8f + random.nextInt((int)(h * 0.4f)); 
             radius = (100 + random.nextInt(300)) * density;
             speed = (0.2f + random.nextFloat() * 0.3f) * density * (random.nextBoolean() ? 1 : -1);
             alpha = 20 + random.nextInt(20);
        }
        
        void update() {
            x += speed;
            if (x - radius > width) x = -radius;
            if (x + radius < 0) x = width + radius;
        }
    }
}
