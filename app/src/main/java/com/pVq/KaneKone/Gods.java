package com.pVq.KaneKone.game;

public class Gods {

    public static int SEED;
    public static final int SEA_LEVEL = 10;
    
    public static final float GRAVITY = -30.0f; 
    public static final float JUMP_HEIGHT = 1.2f; 
    public static final float WALK_SPEED = 6.0f;
    public static final float SWIM_SPEED = 3.5f;
    public static final float PLAYER_WIDTH = 0.6f;  
    public static final float PLAYER_HEIGHT = 1.8f; 
    
    public static void setSeed(long s) { SEED = (int) (s % Integer.MAX_VALUE); }

    public static float getGroundY(float x, float z) {
        float macro = noise(x * 0.001f, z * 0.001f); 
        float micro = noise(x * 0.03f, z * 0.03f) * 0.05f;
        float h = (macro + micro + 1.0f) * 0.5f;
        
        float threshold = 0.7f;
        if (h < threshold) {
            float t = h / threshold; 
            return 5.0f + (t * 20.0f); 
        } else {
            float t = (h - threshold) / (1.0f - threshold); 
            return 25.0f + (t * t * 75.0f);
        }
    }
    
    public static boolean isTreeAt(int x, int z) {
        float y = getGroundY(x, z);
        if (y < SEA_LEVEL + 2.0f || y > 40.0f) return false;

        if (x % 5 != 0 || z % 5 != 0) return false;

        float density = noise(x * 0.05f + 500, z * 0.05f + 500);
        
        boolean randomChance = rand(x, z) > 0.3f; 
        
        return (density > 0.4f) && randomChance;
    }
    
    public static boolean isRockAt(int x, int z) {
        float y = getGroundY(x, z);
        if (y < SEA_LEVEL + 1.0f) return false;
        if (x % 7 != 0 || z % 7 != 0) return false;
        return noise(x * 0.3f, z * 0.3f) > 0.6f;
    }
    
    public static float getSmoothHeight(float x, float z) {
        int ix = (int)Math.floor(x); int iz = (int)Math.floor(z);
        float h00 = getGroundY(ix, iz); float h10 = getGroundY(ix+1, iz);
        float h01 = getGroundY(ix, iz+1); float h11 = getGroundY(ix+1, iz+1);
        float fracX = x - ix; float fracZ = z - iz;
        if (fracZ > fracX) return h00 + (h01 - h00) * fracZ + (h11 - h01) * fracX;
        else return h00 + (h11 - h10) * fracZ + (h10 - h00) * fracX;
    }
    public static boolean isSolid(float x, float y, float z) {
        if (y < -50) return false;
        return y < getGroundY(x, z);
    }
    private static float noise(float x, float z) { 
        int ix = (int)Math.floor(x); int iz = (int)Math.floor(z); 
        float fx = x - ix; float fz = z - iz; 
        float a = rand(ix, iz); float b = rand(ix+1, iz); 
        float c = rand(ix, iz+1); float d = rand(ix+1, iz+1); 
        float ux = fx*fx*(3-2*fx); float uz = fz*fz*(3-2*fz); 
        return a + (b-a)*ux + (c-a)*uz + (a-b-c+d)*ux*uz; 
    }
    private static float rand(int x, int z) { 
        int n = x + z * 57 + SEED; n = (n<<13)^n; 
        return (1.0f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0f); 
    }
    public static float rotLerp(float cur, float target, float t) { 
        float diff = target - cur; while (diff < -180) diff += 360; while (diff > 180) diff -= 360; return cur + diff * t; 
    }
    public static class AABB {
        public float x0, y0, z0, x1, y1, z1;
        public AABB(float x, float y, float z, float w, float h) { setPosition(x, y, z, w, h); }
        public void setPosition(float x, float y, float z, float w, float h) { this.x0 = x - w/2; this.y0 = y; this.z0 = z - w/2; this.x1 = x + w/2; this.y1 = y + h; this.z1 = z + w/2; }
        public void move(float dx, float dy, float dz) { x0 += dx; x1 += dx; y0 += dy; y1 += dy; z0 += dz; z1 += dz; }
        public AABB clone() { AABB c = new AABB(0,0,0,0,0); c.x0=x0; c.y0=y0; c.z0=z0; c.x1=x1; c.y1=y1; c.z1=z1; return c; }
        public void expand(float dx, float dy, float dz) { if (dx < 0) x0 += dx; else x1 += dx; if (dy < 0) y0 += dy; else y1 += dy; if (dz < 0) z0 += dz; else z1 += dz; }
        public float clipX(AABB other, float dx) { if (other.y1 <= y0 || other.y0 >= y1 || other.z1 <= z0 || other.z0 >= z1) return dx; if (dx > 0 && other.x0 >= x1) { float d = other.x0 - x1; if (d < dx) dx = d; } if (dx < 0 && other.x1 <= x0) { float d = other.x1 - x0; if (d > dx) dx = d; } return dx; }
        public float clipZ(AABB other, float dz) { if (other.y1 <= y0 || other.y0 >= y1 || other.x1 <= x0 || other.x0 >= x1) return dz; if (dz > 0 && other.z0 >= z1) { float d = other.z0 - z1; if (d < dz) dz = d; } if (dz < 0 && other.z1 <= z0) { float d = other.z1 - z0; if (d > dz) dz = d; } return dz; }
    }
}
