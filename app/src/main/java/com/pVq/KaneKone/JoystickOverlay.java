package com.pVq.KaneKone.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class JoystickOverlay extends View {

    private GameRenderer renderer;
    
    // --- SETUP JOYSTICK (KIRI) ---
    private float joyCenterX, joyCenterY;
    private float joyTouchX, joyTouchY;
    private float baseRadius = 150f;
    private float hatRadius = 70f;
    private boolean isJoystickActive = false;
    private int joystickPointerId = -1;
    
    // --- SETUP KAMERA (KANAN) ---
    private float lastTouchX, lastTouchY;
    private int cameraPointerId = -1;
    
    private final float TOUCH_SENSITIVITY = 0.2f; 

    // Visual Paint
    private Paint paintBase;
    private Paint paintHat;

    public JoystickOverlay(Context context) {
        super(context);
        
        paintBase = new Paint();
        paintBase.setColor(Color.WHITE);
        paintBase.setAlpha(50); 
        paintBase.setStyle(Paint.Style.FILL);

        paintHat = new Paint();
        paintHat.setColor(Color.WHITE);
        paintHat.setAlpha(150);
        paintHat.setStyle(Paint.Style.FILL);
    }

    public void setRenderer(GameRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isJoystickActive) {
            canvas.drawCircle(joyCenterX, joyCenterY, baseRadius, paintBase);
            canvas.drawCircle(joyTouchX, joyTouchY, hatRadius, paintHat);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (renderer == null) return false;

        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (x < getWidth() / 2) {
                    if (joystickPointerId == -1) {
                        joystickPointerId = id;
                        isJoystickActive = true;
                        joyCenterX = x;
                        joyCenterY = y;
                        joyTouchX = x;
                        joyTouchY = y;
                        invalidate();
                    }
                } else {
                    if (cameraPointerId == -1) {
                        cameraPointerId = id;
                        lastTouchX = x;
                        lastTouchY = y;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    float px = event.getX(i);
                    float py = event.getY(i);

                    // --- UPDATE JOYSTICK (JALAN) ---
                    if (pid == joystickPointerId) {
                        float dx = px - joyCenterX;
                        float dy = py - joyCenterY;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);

                        if (dist > baseRadius) {
                            float ratio = baseRadius / dist;
                            dx *= ratio;
                            dy *= ratio;
                        }

                        joyTouchX = joyCenterX + dx;
                        joyTouchY = joyCenterY + dy;
                        
                        // Hitung nilai normal (-1.0 s/d 1.0)
                        float rawX = dx / baseRadius;
                        float rawY = dy / baseRadius;

                        // [FIX DEADZONE]
                        // Abaikan input di bawah 10% (0.1)
                        if (Math.abs(rawX) < 0.1f) rawX = 0f;
                        if (Math.abs(rawY) < 0.1f) rawY = 0f;
                        
                        renderer.joyX = rawX;
                        renderer.joyY = rawY;
                        
                        invalidate();
                    }
                    
                    // --- UPDATE KAMERA (LIHAT) ---
                    if (pid == cameraPointerId) {
                        float dx = px - lastTouchX;
                        float dy = py - lastTouchY;
                        renderer.camera.rotate(-dx * TOUCH_SENSITIVITY, dy * TOUCH_SENSITIVITY);
                        lastTouchX = px;
                        lastTouchY = py;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (id == joystickPointerId) {
                    isJoystickActive = false;
                    joystickPointerId = -1;
                    renderer.joyX = 0;
                    renderer.joyY = 0;
                    invalidate();
                }
                if (id == cameraPointerId) {
                    cameraPointerId = -1;
                }
                break;
        }
        return true;
    }
}
