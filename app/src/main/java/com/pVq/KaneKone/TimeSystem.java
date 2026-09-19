package com.pVq.KaneKone.game;

public class TimeSystem {
    
    // Waktu Game (0 - 24)
    public float gameTime = 10.0f; // Mulai jam 10 pagi
    
    // Speed: 1 Detik Real = 1 Jam Game (Bisa diatur)
    private final float TIME_SPEED = 1.0f / 60.0f; 

    // Warna Langit saat ini
    public float rSky, gSky, bSky;
    public float sunBrightness; // Kecerahan Matahari (0.2 - 1.0)

    // Palet Warna
    private float[] colorNight   = {0.05f, 0.05f, 0.2f};
    private float[] colorSunrise = {0.8f,  0.4f,  0.8f};
    private float[] colorDay     = {0.4f,  0.8f,  1.0f};
    private float[] colorSunset  = {1.0f,  0.5f,  0.2f};

    public void update(float dt) {
        gameTime += dt * TIME_SPEED;
        if (gameTime >= 24.0f) gameTime -= 24.0f;

        // Logika Pergantian Warna Langit
        if (gameTime >= 0 && gameTime < 5) {
            setColor(colorNight);
        } 
        else if (gameTime >= 5 && gameTime < 8) {
            float t = (gameTime - 5) / 3.0f; 
            lerpColor(colorNight, colorSunrise, t);
        } 
        else if (gameTime >= 8 && gameTime < 12) {
            float t = (gameTime - 8) / 4.0f; 
            lerpColor(colorSunrise, colorDay, t);
        }
        else if (gameTime >= 12 && gameTime < 17) {
            setColor(colorDay);
        }
        else if (gameTime >= 17 && gameTime < 19) {
            float t = (gameTime - 17) / 2.0f; 
            lerpColor(colorDay, colorSunset, t);
        }
        else if (gameTime >= 19 && gameTime < 24) {
            float t = (gameTime - 19) / 5.0f; 
            lerpColor(colorSunset, colorNight, t);
        }
        
        // Hitung Kecerahan Matahari (Untuk shading tanah)
        sunBrightness = (rSky + gSky + bSky) / 3.0f;
        if (sunBrightness < 0.2f) sunBrightness = 0.2f; // Minimal cahaya bulan
    }

    private void setColor(float[] c) {
        rSky = c[0]; gSky = c[1]; bSky = c[2];
    }

    private void lerpColor(float[] c1, float[] c2, float t) {
        rSky = c1[0] + (c2[0] - c1[0]) * t;
        gSky = c1[1] + (c2[1] - c1[1]) * t;
        bSky = c1[2] + (c2[2] - c1[2]) * t;
    }
}
