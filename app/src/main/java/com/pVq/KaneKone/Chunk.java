package com.pVq.KaneKone.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import android.opengl.GLES20;

public class Chunk {
    
    private static final float[] COLOR_GRASS = {0.35f, 0.75f, 0.35f, 1.0f}; 
    private static final float[] COLOR_DIRT  = {0.55f, 0.40f, 0.25f, 1.0f}; 
    private static final float[] COLOR_STONE = {0.60f, 0.65f, 0.70f, 1.0f};
    private static final float[] COLOR_SNOW  = {0.95f, 0.98f, 1.0f, 1.0f};
    private static final float[] COLOR_SAND  = {0.85f, 0.80f, 0.55f, 1.0f};
    
    private static final float[] LEAF_OAK_DARK  = {0.1f, 0.4f, 0.1f, 1.0f};
    private static final float[] LEAF_OAK_LIGHT = {0.3f, 0.7f, 0.2f, 1.0f};
    private static final float[] LEAF_SAKURA_DARK  = {0.8f, 0.4f, 0.5f, 1.0f};
    private static final float[] LEAF_SAKURA_LIGHT = {1.0f, 0.7f, 0.8f, 1.0f};
    private static final float[] LEAF_MYSTIC_DARK  = {0.4f, 0.1f, 0.6f, 1.0f};
    private static final float[] LEAF_MYSTIC_LIGHT = {0.6f, 0.3f, 0.9f, 1.0f};
    
    private static final float[] COLOR_TRUNK  = {0.35f, 0.25f, 0.15f, 1.0f};
    private static final float[] COLOR_ROCK   = {0.5f, 0.5f, 0.55f, 1.0f};
    private static final float[] COLOR_BUSH   = {0.4f, 0.8f, 0.3f, 1.0f};

    FloatBuffer vertexBuffer;
    FloatBuffer colorBuffer;
    int vertexCount;

    public Chunk(int cx, int cz) {
        generateSmoothMesh(cx, cz);
    }

    private void generateSmoothMesh(int cx, int cz) {
        int startX = cx * 16;
        int startZ = cz * 16;
        
        List<Float> verts = new ArrayList<>();
        List<Float> cols = new ArrayList<>();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                
                float h00 = Gods.getGroundY(x, z);     
                float h10 = Gods.getGroundY(x+1, z);   
                float h01 = Gods.getGroundY(x, z+1);   
                float h11 = Gods.getGroundY(x+1, z+1); 
                
                float avgH = (h00 + h10 + h01 + h11) / 4.0f;
                float[] baseColor = getColorByHeight(avgH);

                addTriDoubleSided(verts, cols, x, h00, z, x, h01, z+1, x+1, h11, z+1, baseColor);
                addTriDoubleSided(verts, cols, x, h00, z, x+1, h11, z+1, x+1, h10, z, baseColor);
                
                float lowest = Math.min(Math.min(h00, h10), Math.min(h01, h11));
                checkAndDrawSkirt(verts, cols, x+1, z, lowest, h10, h11, 1, 0, baseColor); 
                checkAndDrawSkirt(verts, cols, x-1, z, lowest, h01, h00, -1, 0, baseColor); 
                checkAndDrawSkirt(verts, cols, x, z+1, lowest, h11, h01, 0, 1, baseColor); 
                checkAndDrawSkirt(verts, cols, x, z-1, lowest, h00, h10, 0, -1, baseColor); 

                
                if (Gods.isTreeAt(x, z)) {
                    float jitterX = (float)Math.sin(x * 12.9898 + z * 78.233) * 2.0f; 
                    float jitterZ = (float)Math.cos(x * 39.346 + z * 11.135) * 2.0f;

                    float px = x + 0.5f + jitterX;
                    float pz = z + 0.5f + jitterZ;
                    float py = Gods.getSmoothHeight(px, pz);
                    
                    float biomeNoise = (float)Math.sin(x * 0.02f) + (float)Math.cos(z * 0.02f);
                    int treeType = 0;
                    if (biomeNoise > 1.2f) treeType = 1;
                    else if (biomeNoise < -1.2f) treeType = 2;
                    
                    buildIsekaiTree(verts, cols, px, py, pz, treeType);
                }
                
                else if (Gods.isRockAt(x, z)) {
                    float px = x + 0.5f; 
                    float pz = z + 0.5f;
                    float py = Gods.getSmoothHeight(px, pz);
                    buildRockMesh(verts, cols, px, py, pz);
                }
                
                else if (avgH > Gods.SEA_LEVEL + 1.5f && avgH < 35) {
                    if (Math.random() > 0.7) {
                        float gx = x + 0.5f + (float)(Math.random()*0.6f - 0.3f);
                        float gz = z + 0.5f + (float)(Math.random()*0.6f - 0.3f);
                        float gy = Gods.getSmoothHeight(gx, gz);
                        buildGrass(verts, cols, gx, gy, gz);
                    }
                }
            }
        }
        
        vertexCount = verts.size() / 3;
        
        ByteBuffer bb = ByteBuffer.allocateDirect(verts.size() * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        for (float f : verts) vertexBuffer.put(f);
        vertexBuffer.position(0);

        ByteBuffer cb = ByteBuffer.allocateDirect(cols.size() * 4);
        cb.order(ByteOrder.nativeOrder());
        colorBuffer = cb.asFloatBuffer();
        for (float f : cols) colorBuffer.put(f);
        colorBuffer.position(0);
    }
    
    private void buildIsekaiTree(List<Float> v, List<Float> c, float x, float y, float z, int type) {
        y -= 0.2f;

        float[] cDark = LEAF_OAK_DARK;
        float[] cLight = LEAF_OAK_LIGHT;
        
        if (type == 1) { cDark = LEAF_SAKURA_DARK; cLight = LEAF_SAKURA_LIGHT; }
        if (type == 2) { cDark = LEAF_MYSTIC_DARK; cLight = LEAF_MYSTIC_LIGHT; }

        float randH = Math.abs((float)Math.sin(x*z)) * 6.0f; 
        float hTrunk = 8.0f + randH; 
        
        float w = 0.6f + (randH * 0.05f); 
        
        addBox(v, c, x, y, z, w*2, hTrunk, w*2, COLOR_TRUNK); 
        

        float l1_y = y + hTrunk - 2.5f;
        float w1 = 3.5f + (randH * 0.1f);
        addBox(v, c, x, l1_y, z, w1*2, 2.5f, w1*2, cDark); 
        
        float l2_y = l1_y + 2.0f;
        float w2 = w1 * 0.7f;
        addBox(v, c, x, l2_y, z, w2*2, 2.5f, w2*2, cDark); 
        addBox(v, c, x+0.4f, l2_y+0.2f, z+0.4f, w2*2.1f, 2.0f, w2*2.1f, cLight); 
        
        float l3_y = l2_y + 2.0f;
        float w3 = w2 * 0.6f;
        addBox(v, c, x, l3_y, z, w3*2, 2.0f, w3*2, cLight); 
    }
    
    private void buildGrass(List<Float> v, List<Float> c, float x, float y, float z) {
        float w = 0.4f; 
        float h = 0.7f;
        addQuadDoubleSided(v, c, x-w, y, z-w, x-w, y+h, z-w, x+w, y+h, z+w, x+w, y, z+w, COLOR_BUSH);
        addQuadDoubleSided(v, c, x+w, y, z-w, x+w, y+h, z-w, x-w, y+h, z+w, x-w, y, z+w, COLOR_BUSH);
    }
    
    private void buildRockMesh(List<Float> v, List<Float> c, float x, float y, float z) {
        y += 0.1f; 
        float s = 0.5f + (float)(Math.random()*0.3f); 
        float h = 0.4f + (float)(Math.random()*0.4f);
        addBox(v, c, x, y, z, s, h, s, COLOR_ROCK);
    }

    private void addBox(List<Float> v, List<Float> c, float x, float y, float z, float w, float h, float d, float[] color) {
        float hw = w/2; float hd = d/2;
        addQuadDoubleSided(v, c, x-hw, y, z+hd, x-hw, y+h, z+hd, x+hw, y+h, z+hd, x+hw, y, z+hd, color);
        addQuadDoubleSided(v, c, x+hw, y, z-hd, x+hw, y+h, z-hd, x-hw, y+h, z-hd, x-hw, y, z-hd, color);
        addQuadDoubleSided(v, c, x-hw, y, z-hd, x-hw, y+h, z-hd, x-hw, y+h, z+hd, x-hw, y, z+hd, color);
        addQuadDoubleSided(v, c, x+hw, y, z+hd, x+hw, y+h, z+hd, x+hw, y+h, z-hd, x+hw, y, z-hd, color);
        addQuadDoubleSided(v, c, x-hw, y+h, z+hd, x-hw, y+h, z-hd, x+hw, y+h, z-hd, x+hw, y+h, z+hd, color);
    }
    
    private void addQuadDoubleSided(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float[] color) {
        addTri(v, c, x1, y1, z1, x2, y2, z2, x3, y3, z3, color);
        addTri(v, c, x1, y1, z1, x3, y3, z3, x4, y4, z4, color);
        addTri(v, c, x1, y1, z1, x3, y3, z3, x2, y2, z2, color);
        addTri(v, c, x1, y1, z1, x4, y4, z4, x3, y3, z3, color);
    }
    
    private void addTriDoubleSided(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] color) {
        addTri(v, c, x1, y1, z1, x2, y2, z2, x3, y3, z3, color);
        addTri(v, c, x1, y1, z1, x3, y3, z3, x2, y2, z2, color);
    }

    private void addTri(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] color) {
        float noise = (float)(Math.random() * 0.05f) - 0.025f;
        float r = Math.max(0, Math.min(1, color[0] + noise));
        float g = Math.max(0, Math.min(1, color[1] + noise));
        float b = Math.max(0, Math.min(1, color[2] + noise));
        float a = color[3];

        v.add(x1); v.add(y1); v.add(z1); c.add(r); c.add(g); c.add(b); c.add(a);
        v.add(x2); v.add(y2); v.add(z2); c.add(r); c.add(g); c.add(b); c.add(a);
        v.add(x3); v.add(y3); v.add(z3); c.add(r); c.add(g); c.add(b); c.add(a);
    }
    
    private float[] getColorByHeight(float y) {
        if (y < Gods.SEA_LEVEL + 1.5f) return COLOR_SAND;
        if (y < 40) return COLOR_GRASS;
        if (y < 65) return COLOR_STONE;
        return COLOR_SNOW;
    }

    private void checkAndDrawSkirt(List<Float> v, List<Float> c, int nx, int nz, float myBase, float h1, float h2, int dx, int dz, float[] color) {
        float neighborH = Gods.getGroundY(nx, nz);
        if (neighborH < myBase - 1.0f) { 
            float[] darkColor = {color[0]*0.7f, color[1]*0.7f, color[2]*0.7f, 1.0f};
            float x1, z1, x2, z2;
            if (dx == 1) { x1 = nx; z1 = nz; x2 = nx; z2 = nz+1; } else if (dx == -1) { x1 = nx+1; z1 = nz+1; x2 = nx+1; z2 = nz; } else if (dz == 1) { x1 = nx+1; z1 = nz; x2 = nx; z2 = nz; } else { x1 = nx; z1 = nz+1; x2 = nx+1; z2 = nz+1; }
            addQuadDoubleSided(v, c, x1, h1, z1, x1, neighborH, z1, x2, neighborH, z2, x2, h2, z2, darkColor);
        }
    }

    public void draw(int program) {
        if (vertexCount == 0) return;
        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        
        int colHandle = GLES20.glGetAttribLocation(program, "av_Color");
        
        GLES20.glEnableVertexAttribArray(posHandle); GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(colHandle); GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        GLES20.glDisableVertexAttribArray(posHandle); GLES20.glDisableVertexAttribArray(colHandle);
    }
}
