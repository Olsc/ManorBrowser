package com.olsc.manorbrowser.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SakuraView extends View {

    private Paint skyPaint;
    private Paint groundPaint;
    private Paint petalPaint; 

    private int width, height;
    private float density;
    private final Random random = new Random();

    private Bitmap treeBitmap;
    private Canvas treeCanvas;

    private final List<Petal> petals = new ArrayList<>();
    private Path groundPath;

    public SakuraView(Context context) {
        this(context, null);
    }

    public SakuraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SakuraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        skyPaint.setStyle(Paint.Style.FILL);

        groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        groundPaint.setStyle(Paint.Style.FILL);

        petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        petalPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        LinearGradient skyGradient = new LinearGradient(
                0, 0, 0, height,
                new int[]{0xFFE3F2FD, 0xFFF3E5F5, 0xFFFFFDE7}, 
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);
        skyPaint.setShader(skyGradient);
        
        LinearGradient groundGradient = new LinearGradient(
                0, height * 0.8f, 0, height,
                new int[]{0xFFFCE4EC, 0xFFF8BBD0}, 
                null, Shader.TileMode.CLAMP);
        groundPaint.setShader(groundGradient);
        
        groundPath = new Path();
        groundPath.moveTo(0, height);
        groundPath.lineTo(0, height * 0.85f);
        groundPath.cubicTo(width * 0.4f, height * 0.82f, width * 0.7f, height * 0.88f, width, height * 0.85f);
        groundPath.lineTo(width, height);
        groundPath.close();

        generateTreeBitmap(); 
        generatePetals();
    }
    
    private void generateTreeBitmap() {
        if (width <= 0 || height <= 0) return;
        
        if (treeBitmap != null && !treeBitmap.isRecycled()) {
            treeBitmap.recycle();
        }
        treeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas treeCanvas = new Canvas(treeBitmap); 
        
        Paint branchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        branchPaint.setColor(0xFF5D4037);
        branchPaint.setStyle(Paint.Style.STROKE);
        branchPaint.setStrokeCap(Paint.Cap.ROUND); 

        Paint flowerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flowerPaint.setStyle(Paint.Style.FILL);

        float startX = -10 * density; 
        float startY = height * 0.05f; 
        
        float trunkLen = height * 0.12f;
        float angle = 25;
        float startThickness = 20 * density; 

        growBranch(treeCanvas, branchPaint, flowerPaint, startX, startY, trunkLen, angle, startThickness);
    }

    private void growBranch(Canvas canvas, Paint branchPaint, Paint flowerPaint, 
                            float x, float y, float length, float angle, float thickness) {
        
        if (thickness < 1.0 * density || length < 5 * density) {
             placeFlowerCluster(canvas, flowerPaint, x, y);
             return;
        }

        double rAngle = Math.toRadians(angle);
        float endX = x + (float) Math.cos(rAngle) * length;
        float endY = y + (float) Math.sin(rAngle) * length;

        branchPaint.setStrokeWidth(thickness);
        canvas.drawLine(x, y, endX, endY, branchPaint);

        if (thickness < 12 * density && random.nextFloat() > 0.3f) {
            float t = random.nextFloat();
            float fx = x + (endX - x) * t;
            float fy = y + (endY - y) * t;
            placeFlowerCluster(canvas, flowerPaint, fx, fy);
        }

        // Recursive branching
        int numChildren = 2; // Keep at 2 for performance/aesthetics

        for (int i = 0; i < numChildren; i++) {
            float newLen = length * (0.6f + random.nextFloat() * 0.2f); // Shorter decay
            float newThick = thickness * 0.7f;
            
            float angleDev = (random.nextFloat() - 0.5f) * 60f; 
            float newAngle = angle + angleDev; 

            if (thickness < 8 * density) {
                newAngle += 20 * random.nextFloat(); // Bias downwards (positive angle increases y)
            }

            growBranch(canvas, branchPaint, flowerPaint, endX, endY, newLen, newAngle, newThick);
        }
    }

    private void placeFlowerCluster(Canvas canvas, Paint paint, float x, float y) {
        int count = 4 + random.nextInt(4); 
        for(int i=0; i<count; i++) {
            float r = (4 + random.nextFloat() * 5) * density;
            float ox = (random.nextFloat() - 0.5f) * 35 * density;
            float oy = (random.nextFloat() - 0.5f) * 35 * density;
            
            float roll = random.nextFloat();
            if (roll > 0.9) paint.setColor(0xFFFFFFFF); // White
            else if (roll > 0.6) paint.setColor(0xFFFF80AB); // Deep Pink
            else paint.setColor(0xFFFFCDD2); // Standard
            
            canvas.drawCircle(x + ox, y + oy, r, paint);
        }
    }

    private void generatePetals() {
        petals.clear();
        int count = 50; // Performance friendly count
        for (int i=0; i<count; i++) {
            petals.add(new Petal(width, height, density));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); 

        canvas.drawRect(0, 0, width, height, skyPaint);
        canvas.drawPath(groundPath, groundPaint);

        if (treeBitmap != null) {
            canvas.drawBitmap(treeBitmap, 0, 0, null);
        }

        for (Petal petal : petals) {
            petal.update();
            petal.draw(canvas, petalPaint); 
        }

        postInvalidateOnAnimation();
    }


    private class Petal {
        float x, y;
        float size;
        float speedY, speedX;
        float rotation, rotationSpeed;
        int alpha;
        int color;
        float wobblePhase; 

        Petal(int w, int h, float density) {
            reset(w, h, density, true);
        }

        void reset(int w, int h, float density, boolean randomStart) {
            x = random.nextInt(w);
            y = randomStart ? random.nextInt(h) : -30;
            size = (4 + random.nextFloat() * 4) * density; 
            
            speedY = (0.8f + random.nextFloat() * 1.5f); 
            speedX = (random.nextFloat() - 0.5f) * 1.5f; 
            
            rotation = random.nextFloat() * 360;
            rotationSpeed = (random.nextFloat() - 0.5f) * 3f;
            wobblePhase = random.nextFloat() * 100;
            
            alpha = 150 + random.nextInt(105);
            
            if (random.nextBoolean()) color = 0xFFFF80AB;
            else color = 0xFFFFCDD2;
        }

        void update() {
            y += speedY;
            x += speedX + (float)Math.sin(y * 0.015f + wobblePhase) * 1.5f; 
            rotation += rotationSpeed;

            if (y > height + 30) {
                reset(width, height, density, false);
            }
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setAlpha(alpha);
            
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);
            canvas.drawOval(-size/2, -size/3, size/2, size/3, paint);
            canvas.restore();
        }
    }
}
