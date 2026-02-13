package com.olsc.manorbrowser.view;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AuroraView extends View {

    private Paint bgPaint;
    private Paint starPaint;
    private Paint auroraPaint;
    private int width, height;
    private float density;
    private final Random random = new Random();
    private final List<AuroraCurtain> curtains = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    
    private float time = 0;

    private boolean isPureColorMode = false;
    private int solidColor = 0xFF00FF99; // Default Green
    private ColorWheel colorWheel;
    private boolean showColorWheel = false;

    public AuroraView(Context context) {
        this(context, null);
    }

    public AuroraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AuroraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);

        starPaint = new Paint();
        starPaint.setColor(Color.WHITE);
        starPaint.setAntiAlias(true);
        starPaint.setStyle(Paint.Style.FILL);

        auroraPaint = new Paint();
        auroraPaint.setAntiAlias(true);
        auroraPaint.setStyle(Paint.Style.FILL);
        auroraPaint.setMaskFilter(new BlurMaskFilter(30 * density, BlurMaskFilter.Blur.NORMAL));
        
        colorWheel = new ColorWheel();
    }

    public void setPureColorMode(boolean enabled) {
        this.isPureColorMode = enabled;
        invalidate();
    }
    
    public void setSolidColor(int color) {
        this.solidColor = color;
        if (colorWheel != null) {
            colorWheel.setColor(color);
        }
        invalidate();
    }
    
    public int getSolidColor() {
        return solidColor;
    }
    
    public void setShowColorWheel(boolean show) {
        this.showColorWheel = show;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        LinearGradient bgGradient = new LinearGradient(
                0, 0, 0, height,
                new int[]{0xFF000000, 0xFF0B1026}, 
                null, Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGradient);

        stars.clear();
        for (int i = 0; i < 100; i++) {
            stars.add(new Star(w, h));
        }

        regenerateCurtains();
        colorWheel.updateBounds(w, h, density);
    }

    private void regenerateCurtains() {
        curtains.clear();
        curtains.add(new AuroraCurtain(0xFF00FF99, height * 0.3f, 1.0f)); 
        curtains.add(new AuroraCurtain(0xFF00CCFF, height * 0.4f, 0.8f)); 
        curtains.add(new AuroraCurtain(0xFF9900FF, height * 0.5f, 0.6f)); 
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isPureColorMode) {
            canvas.drawColor(solidColor);
        } else {
            canvas.drawRect(0, 0, width, height, bgPaint);
            for (Star star : stars) {
                star.update();
                starPaint.setAlpha(star.alpha);
                canvas.drawCircle(star.x, star.y, star.size, starPaint);
            }
            time += 0.005f;
            for (AuroraCurtain curtain : curtains) {
                curtain.update(time);
                curtain.draw(canvas, auroraPaint);
            }
        }
        
        if (showColorWheel && isPureColorMode) {
            colorWheel.draw(canvas);
        }

        if (!isPureColorMode) {
            postInvalidateOnAnimation();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (showColorWheel && isPureColorMode) {
             boolean handled = colorWheel.onTouch(event);
             if (handled) {
                 solidColor = colorWheel.getSelectedColor();
                 invalidate();
                 return true;
             }
             if (event.getAction() == MotionEvent.ACTION_DOWN) {
                 return false; // Don't consume if not on wheel
             }
        }
        return super.onTouchEvent(event);
    }

    private class Star {
        float x, y, size;
        int alpha, baseAlpha;
        float twinkleOffset;

        Star(int w, int h) {
            x = random.nextFloat() * w;
            y = random.nextFloat() * h * 0.7f; 
            size = (0.5f + random.nextFloat()) * density;
            baseAlpha = 100 + random.nextInt(155);
            alpha = baseAlpha;
            twinkleOffset = random.nextFloat() * 100;
        }

        void update() {
            double time = System.currentTimeMillis() * 0.002;
            float factor = (float) Math.sin(time + twinkleOffset);
            alpha = (int) (baseAlpha * (0.7f + 0.3f * factor));
        }
    }

    private class AuroraCurtain {
        private final int color;
        private final float baseHeight; 
        private final Path path = new Path();
        private final float speedFactor;
        private final float[] offsets = new float[5];

        AuroraCurtain(int color, float baseHeight, float speedFactor) {
            this.color = color;
            this.baseHeight = baseHeight;
            this.speedFactor = speedFactor;
            for(int i=0; i<5; i++) offsets[i] = random.nextFloat() * 100;
        }

        void update(float t) {
            path.reset();
            path.moveTo(-100, -100); 
            for (int x = -50; x <= width + 50; x += (int)(20 * density)) {
                float wave1 = (float)Math.sin(x * 0.0015f + t * speedFactor + offsets[0]) * (80 * density);
                float wave2 = (float)Math.sin(x * 0.004f + t * 0.5f * speedFactor + offsets[1]) * (50 * density);
                float wave3 = (float)Math.sin(x * 0.01f + t * 2.0f + offsets[2]) * (20 * density);
                float y = baseHeight + wave1 + wave2 + wave3;
                path.lineTo(x, y);
            }
            path.lineTo(width + 100, -100);
            path.close();
        }

        void draw(Canvas canvas, Paint paint) {
            LinearGradient shader = new LinearGradient(
                    0, 0, 0, baseHeight + 100 * density,
                    new int[]{ Color.TRANSPARENT, color, Color.TRANSPARENT },
                    new float[]{ 0.1f, 0.6f, 1.0f }, 
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawPath(path, paint);
        }
    }
    
    private class ColorWheel {
        float cx, cy, radius;
        Paint wheelPaint;
        Paint valueSliderPaint;
        int selectedColor = 0xFF00FF99;
        boolean isDraggingWheel = false;
        boolean isDraggingValue = false;
        
        float currentHue = 150f;
        float currentValue = 1f;

        // Slider Bounds
        float sx, sy, sw, sh;
        
        ColorWheel() {
            wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wheelPaint.setStyle(Paint.Style.STROKE);
            
            valueSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valueSliderPaint.setStyle(Paint.Style.FILL);
        }
        
        void updateBounds(int w, int h, float density) {
            this.radius = 80 * density;
            this.cx = w / 2f;
            this.cy = h / 2f - 40 * density; // Move up a bit to fit slider
            
            int[] colors = new int[] { 
                0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000 
            };
            android.graphics.SweepGradient sweep = new android.graphics.SweepGradient(cx, cy, colors, null);
            wheelPaint.setShader(sweep);
            wheelPaint.setStrokeWidth(30 * density);

            this.sw = 160 * density;
            this.sh = 20 * density;
            this.sx = cx - sw / 2f;
            this.sy = cy + radius + 40 * density;
        }
        
        void draw(Canvas canvas) {
              canvas.drawCircle(cx, cy, radius, wheelPaint);
             
             Paint centerP = new Paint(Paint.ANTI_ALIAS_FLAG);
             centerP.setColor(selectedColor);
             centerP.setStyle(Paint.Style.FILL);
             canvas.drawCircle(cx, cy, radius * 0.6f, centerP);
             
             Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
             borderP.setColor(Color.WHITE);
             borderP.setStyle(Paint.Style.STROKE);
             borderP.setStrokeWidth(3 * density);
             canvas.drawCircle(cx, cy, radius * 0.6f, borderP);

             float[] hsv = new float[]{ currentHue, 1f, 1f };
             int hueColor = Color.HSVToColor(hsv);
             LinearGradient valueGradient = new LinearGradient(sx, sy, sx + sw, sy,
                     new int[]{ Color.BLACK, hueColor }, null, Shader.TileMode.CLAMP);
             valueSliderPaint.setShader(valueGradient);
             
             // Round rect for the slider track
             canvas.drawRoundRect(sx, sy, sx + sw, sy + sh, sh/2, sh/2, valueSliderPaint);
             
             // Draw slider handle
             float handleX = sx + currentValue * sw;
             Paint handleP = new Paint(Paint.ANTI_ALIAS_FLAG);
             handleP.setColor(Color.WHITE);
             handleP.setStyle(Paint.Style.FILL);
             canvas.drawCircle(handleX, sy + sh / 2f, sh * 0.7f, handleP);
             
             handleP.setStyle(Paint.Style.STROKE);
             handleP.setColor(Color.LTGRAY);
             handleP.setStrokeWidth(2 * density);
             canvas.drawCircle(handleX, sy + sh / 2f, sh * 0.7f, handleP);
        }
        
        boolean onTouch(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx*dx + dy*dy);
                if (dist >= radius - 25 * density && dist <= radius + 25 * density) {
                    isDraggingWheel = true;
                    updateFromWheel(dx, dy);
                    return true;
                }
                if (x >= sx - 20*density && x <= sx + sw + 20*density && 
                    y >= sy - 20*density && y <= sy + sh + 20*density) {
                    isDraggingValue = true;
                    updateFromSlider(x);
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (isDraggingWheel) {
                    updateFromWheel(x - cx, y - cy);
                    return true;
                }
                if (isDraggingValue) {
                    updateFromSlider(x);
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                isDraggingWheel = false;
                isDraggingValue = false;
                return true;
            }
            return false;
        }

        private void updateFromWheel(float dx, float dy) {
            double angle = Math.atan2(dy, dx);
            if (angle < 0) angle += 2 * Math.PI;
            currentHue = (float) (angle / (2 * Math.PI)) * 360f;
            updateSelectedColor();
        }

        private void updateFromSlider(float x) {
            currentValue = (x - sx) / sw;
            if (currentValue < 0) currentValue = 0;
            if (currentValue > 1) currentValue = 1;
            updateSelectedColor();
        }

        private void updateSelectedColor() {
            selectedColor = Color.HSVToColor(new float[]{ currentHue, 1f, currentValue });
        }
        
        void setColor(int color) {
            this.selectedColor = color;
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            currentHue = hsv[0];
            currentValue = hsv[2];
        }

        int getSelectedColor() {
            return selectedColor;
        }
    }
}
