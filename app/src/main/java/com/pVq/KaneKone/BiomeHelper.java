package com.pVq.KaneKone.game;

public class BiomeHelper {
    public static void getColorByHeight(float height, float[] outputColor, int offset) {
        float r, g, b;
        
        if (height < -1.2f) {
            r=0.1f; g=0.1f; b=0.5f; 
        } else if (height < -0.5f) {
            r=0.2f; g=0.4f; b=0.8f; 
        } else if (height < 1.5f) {
            r=0.9f; g=0.8f; b=0.5f; 
        } else if (height < 10.0f) {
            if (((int)(height * 10)) % 2 == 0) {
                r=0.2f; g=0.7f; b=0.2f;
            } else {
                r=0.25f; g=0.75f; b=0.25f;
            }
        } else {
            if (((int)(height * 10)) % 2 == 0) {
                r=0.5f; g=0.5f; b=0.5f; 
            } else {
                r=0.55f; g=0.55f; b=0.55f; 
            }
        }
        outputColor[offset] = r;
        outputColor[offset+1] = g;
        outputColor[offset+2] = b;
        outputColor[offset+3] = 1.0f; 
    }
}
