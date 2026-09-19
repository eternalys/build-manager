package com.pVq.KaneKone.game;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonEngine {

    private int[][] map;
    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;
    private int vertexCount;
    private Random rand = new Random();
    
    private float wallHeight = 18.0f;
    private float cellSize = 6.0f; 
    public static final float FLOOR_Y = 20.0f; 
    
    private float[] COL_WALL_BOT = {0.05f, 0.05f, 0.08f, 1.0f}; 
    private float[] COL_WALL_TOP = {0.15f, 0.15f, 0.22f, 1.0f}; 
    private float[] COL_FLOOR    = {0.08f, 0.08f, 0.10f, 1.0f}; 
    private float[] COL_ALTAR_BASE = {0.1f, 0.1f, 0.1f, 1.0f}; 
    private float[] COL_ALTAR_GLOW = {0.0f, 1.0f, 0.8f, 1.0f}; 

    public DungeonEngine() {
        map = DungeonGenerator.generate(125, 125);
        buildMesh();
    }
    
    public int[][] getMapData() {
        return map;
    }
    
    public float getStartX() { 
        for(int z=1; z<map.length-1; z++) {
            for(int x=1; x<map[0].length-1; x++) { if(map[z][x] == 9) return x * cellSize + (cellSize/2); }
        } 
        int mid = map.length / 2;
        return mid * cellSize; 
    }

    public float getStartZ() { 
        for(int z=1; z<map.length-1; z++) {
            for(int x=1; x<map[0].length-1; x++) { if(map[z][x] == 9) return z * cellSize + (cellSize/2); }
        } 
        int mid = map.length / 2;
        return mid * cellSize; 
    }
    
    public boolean checkCollision(float px, float pz, float radius) {
        float checkRad = radius * 0.8f; 
        if (isSolid(px + checkRad, pz)) return true;
        if (isSolid(px - checkRad, pz)) return true;
        if (isSolid(px, pz + checkRad)) return true;
        if (isSolid(px, pz - checkRad)) return true;
        return false;
    }

    private boolean isSolid(float worldX, float worldZ) {
        int gridX = (int) Math.floor(worldX / cellSize);
        int gridZ = (int) Math.floor(worldZ / cellSize);
        
        if (gridZ < 0 || gridZ >= map.length || gridX < 0 || gridX >= map[0].length) return true;
        
        int id = map[gridZ][gridX];
        return (id == 1 || id == 2 || id == 6);
    }
    
    public boolean isWall(float worldX, float worldZ) { return isSolid(worldX, worldZ); }

    private void buildMesh() {
        List<Float> v = new ArrayList<>();
        List<Float> c = new ArrayList<>();
        
        for (int z = 0; z < map.length; z++) {
            for (int x = 0; x < map[0].length; x++) {
                int type = map[z][x];
                
                if (type == 1 && isSurroundedByWall(x, z)) continue; 
                
                float px = x * cellSize;
                float pz = z * cellSize;
                
                if (type != 1) { 
                    float ceilingY = FLOOR_Y + wallHeight;
                    addRect(v, c, px, ceilingY, pz+cellSize, px+cellSize, ceilingY, pz, COL_WALL_BOT);
                }

                if (type == 1) {
                    addChaoticBlock(v, c, px, FLOOR_Y, pz, cellSize, wallHeight, cellSize, 0.8f);
                }
                else if (type == 2) {
                    addRect(v, c, px, FLOOR_Y, pz, px+cellSize, FLOOR_Y, pz+cellSize, COL_FLOOR);
                    float margin = cellSize * 0.25f;
                    float pW = cellSize - (margin*2);
                    addChaoticBlock(v, c, px+margin, FLOOR_Y, pz+margin, pW, wallHeight, pW, 0.4f);
                }
                else if (type == 6) {
                    buildFloatingAltar(v, c, px, FLOOR_Y, pz);
                }
                else {
                    addRect(v, c, px, FLOOR_Y, pz, px+cellSize, FLOOR_Y, pz+cellSize, COL_FLOOR);
                }
            }
        }
        vertexCount = v.size() / 3;
        vertexBuffer = floatBuf(v);
        colorBuffer = floatBuf(c);
    }
    
    private boolean isSurroundedByWall(int x, int z) {
        if (x<=0 || x>=map[0].length-1 || z<=0 || z>=map.length-1) return false;
        return (map[z+1][x]==1 && map[z-1][x]==1 && map[z][x+1]==1 && map[z][x-1]==1);
    }
    
    private void buildFloatingAltar(List<Float> v, List<Float> c, float x, float y, float z) {
        float cx = x + cellSize/2; float cz = z + cellSize/2;
        addRect(v, c, x, y, z, x+cellSize, y, z+cellSize, COL_ALTAR_BASE);
        float floatH = y + 2.5f;
        float size = cellSize * 0.6f; float hSize = size / 2;
        float[] tipColor = COL_ALTAR_GLOW; float[] baseColor = COL_ALTAR_BASE;
        addTriGradient(v, c, cx-hSize, floatH, cz-hSize, cx+hSize, floatH, cz-hSize, cx, floatH-1.5f, cz, baseColor, baseColor, tipColor);
        addTriGradient(v, c, cx+hSize, floatH, cz-hSize, cx+hSize, floatH, cz+hSize, cx, floatH-1.5f, cz, baseColor, baseColor, tipColor);
        addTriGradient(v, c, cx+hSize, floatH, cz+hSize, cx-hSize, floatH, cz+hSize, cx, floatH-1.5f, cz, baseColor, baseColor, tipColor);
        addTriGradient(v, c, cx-hSize, floatH, cz+hSize, cx-hSize, floatH, cz-hSize, cx, floatH-1.5f, cz, baseColor, baseColor, tipColor);
        addChaoticBlock(v, c, cx-hSize, floatH, cz-hSize, size, 0.5f, size, 0.1f);
        float artSize = 0.8f; float artH = floatH + 0.5f;
        addChaoticBlock(v, c, cx - artSize/2, artH, cz - artSize/2, artSize, artSize, artSize, 0.2f);
        for(int i=0; i<8; i++) {
            double angle = (Math.PI * 2 * i) / 8;
            float dist = cellSize * 0.45f;
            float rx = cx + (float)Math.cos(angle) * dist;
            float rz = cz + (float)Math.sin(angle) * dist;
            float ry = floatH + (rand.nextFloat() - 0.5f) * 1.5f;
            float rScale = 0.3f;
            addCrystalSpike(v, c, rx, ry, rz, rScale, rScale, COL_ALTAR_BASE);
        }
        float[] beamCol = {0.0f, 1.0f, 1.0f, 0.0f}; float[] beamCore = {0.0f, 1.0f, 1.0f, 0.3f};
        float beamH = 10.0f;
        addRectVerticalGradient(v, c, cx-0.1f, artH, cz-0.1f, cx+0.1f, artH+beamH, cz+0.1f, beamCore, beamCol);
        addRectVerticalGradient(v, c, cx+0.1f, artH, cz-0.1f, cx-0.1f, artH+beamH, cz+0.1f, beamCore, beamCol);
    }
    
    private void addChaoticBlock(List<Float> v, List<Float> c, float x, float y, float z, float w, float h, float d, float chaos) {
        float x1=x, z1=z+d; float x2=x+w, z2=z+d; float x3=x+w, z3=z; float x4=x, z4=z;
        float tilt = 2.0f * chaos;
        float tx1 = x + jitter(tilt); float tz1 = z+d + jitter(tilt);
        float tx2 = x+w + jitter(tilt); float tz2 = z+d + jitter(tilt);
        float tx3 = x+w + jitter(tilt); float tz3 = z + jitter(tilt);
        float tx4 = x + jitter(tilt); float tz4 = z + jitter(tilt);
        float ty = y + h + jitter(1.0f * chaos);
        addQuadGradient(v,c, x1,y,z1, x2,y,z2, tx2,ty,tz2, tx1,ty,tz1, COL_WALL_BOT, COL_WALL_TOP);
        addQuadGradient(v,c, x2,y,z2, x3,y,z3, tx3,ty,tz3, tx2,ty,tz2, COL_WALL_BOT, COL_WALL_TOP);
        addQuadGradient(v,c, x3,y,z3, x4,y,z4, tx4,ty,tz4, tx3,ty,tz3, COL_WALL_BOT, COL_WALL_TOP);
        addQuadGradient(v,c, x4,y,z4, x1,y,z1, tx1,ty,tz1, tx4,ty,tz4, COL_WALL_BOT, COL_WALL_TOP);
        addQuadGradient(v,c, tx1,ty,tz1, tx2,ty,tz2, tx3,ty,tz3, tx4,ty,tz4, COL_WALL_TOP, COL_WALL_TOP);
    }
    
    private float jitter(float val) { return (rand.nextFloat() * 2.0f * val) - val; }
    private void addCrystalSpike(List<Float> v, List<Float> c, float x, float y, float z, float w, float h, float[] color) {
        float hw = w/2; float px = x + jitter(w*0.3f); float pz = z + jitter(w*0.3f); float py = y+h;
        float[] tipCol = {color[0]+0.3f, color[1]+0.3f, color[2]+0.3f, 1.0f};
        addTriGradient(v, c, x-hw,y,z+hw, x+hw,y,z+hw, px,py,pz, color, color, tipCol);
        addTriGradient(v, c, x+hw,y,z+hw, x+hw,y,z-hw, px,py,pz, color, color, tipCol);
        addTriGradient(v, c, x+hw,y,z-hw, x-hw,y,z-hw, px,py,pz, color, color, tipCol);
        addTriGradient(v, c, x-hw,y,z-hw, x-hw,y,z+hw, px,py,pz, color, color, tipCol);
    }
    private void addRect(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float[] col) {
        addTriGradient(v, c, x1,y1,z1, x2,y1,z1, x1,y1,z2, col, col, col);
        addTriGradient(v, c, x2,y1,z1, x2,y1,z2, x1,y1,z2, col, col, col);
    }
    private void addRectVerticalGradient(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float[] colBot, float[] colTop) {
        addTriGradient(v, c, x1,y1,z1, x2,y1,z2, x1,y2,z1, colBot, colBot, colTop); 
        addTriGradient(v, c, x2,y1,z2, x2,y2,z2, x1,y2,z1, colBot, colTop, colTop);
    }
    private void addQuadGradient(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float[] colBot, float[] colTop) {
        addTriGradient(v, c, x1,y1,z1, x2,y2,z2, x3,y3,z3, colBot, colBot, colTop);
        addTriGradient(v, c, x1,y1,z1, x3,y3,z3, x4,y4,z4, colBot, colTop, colTop);
    }
    private void addTriGradient(List<Float> v, List<Float> c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] c1, float[] c2, float[] c3) {
        v.add(x1); v.add(y1); v.add(z1); c.add(c1[0]); c.add(c1[1]); c.add(c1[2]); c.add(c1[3]);
        v.add(x2); v.add(y2); v.add(z2); c.add(c2[0]); c.add(c2[1]); c.add(c2[2]); c.add(c2[3]);
        v.add(x3); v.add(y3); v.add(z3); c.add(c3[0]); c.add(c3[1]); c.add(c3[2]); c.add(c3[3]);
    }
    private FloatBuffer floatBuf(List<Float> list) {
        ByteBuffer bb = ByteBuffer.allocateDirect(list.size() * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        for (float f : list) fb.put(f);
        fb.position(0); return fb;
    }
    public void draw(int program) {
        if (vertexCount == 0) return;
        int posH = GLES20.glGetAttribLocation(program, "vPosition");
        int colH = GLES20.glGetAttribLocation(program, "av_Color");
        GLES20.glEnableVertexAttribArray(posH); GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(colH); GLES20.glVertexAttribPointer(colH, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        GLES20.glDisableVertexAttribArray(posH); GLES20.glDisableVertexAttribArray(colH);
    }
}
