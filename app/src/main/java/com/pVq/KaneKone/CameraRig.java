package com.pVq.KaneKone.game;

public class CameraRig {

    private float yaw = 0f;
    private float pitch = 0f;
    
    public float distance = 4.0f; 

    private static final float PITCH_MAX = 80.0f;
    private static final float PITCH_MIN = -45.0f;

    public CameraRig() {
        reset();
    }
    
    public void reset() {
        yaw = 0;
        pitch = 20;
    }


    public void rotate(float dx, float dy) {
        yaw += dx;
        pitch += dy;
        
        if (pitch > PITCH_MAX) pitch = PITCH_MAX;
        if (pitch < PITCH_MIN) pitch = PITCH_MIN;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
