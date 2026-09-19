package com.pVq.KaneKone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ItemManager {

    public static class GameItem {
        float x, y, z;
        int type; float floatOffset;
        public GameItem(float x, float y, float z, int type) {
            this.x = x;
            this.y = y; this.z = z; this.type = type;
            this.floatOffset = (float)Math.random() * 3.14f;
        }
    }

    public List<GameItem> items = Collections.synchronizedList(new ArrayList<GameItem>());
    private FloatBuffer vertexBuffer;
    private float[] mModelMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];
    private float[] COL_GOLD = {1.0f, 0.8f, 0.0f, 1.0f};
    private float[] COL_HP   = {1.0f, 0.2f, 0.2f, 1.0f};
    private float[] COL_MANA = {0.2f, 0.6f, 1.0f, 1.0f};

    public ItemManager() {
        float s = 0.25f;
        float[] coords = { -s,-s,s, s,-s,s, s,s,s, -s,-s,s, s,s,s, -s,s,s, s,-s,-s, -s,-s,-s, -s,s,-s, s,-s,-s, -s,s,-s, s,s,-s, -s,s,s, s,s,s, s,s,-s, -s,s,s, s,s,-s, -s,s,-s, -s,-s,-s, s,-s,-s, s,-s,s, -s,-s,-s, s,-s,s, -s,-s,s, s,-s,s, s,-s,-s, s,s,-s, s,-s,s, s,s,-s, s,s,s, -s,-s,-s, -s,-s,s, -s,s,s, -s,-s,-s, -s,s,s, -s,s,-s };
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer(); vertexBuffer.put(coords); vertexBuffer.position(0);
    }
    
    public void spawnItem(float x, float y, float z, int type) {
        synchronized(items) { items.add(new GameItem(x, y, z, type));
        }
    }
    
    public int tryPickup(float px, float py, float pz) {
        synchronized(items) { 
            Iterator<GameItem> iter = items.iterator();
            while (iter.hasNext()) {
                GameItem item = iter.next();
                float dx = px - item.x; float dy = py - item.y; float dz = pz - item.z;
                if (Math.sqrt(dx*dx + dy*dy + dz*dz) < 1.5f) {
                    int gotType = item.type;
                    iter.remove(); return gotType;
                }
            }
        }
        return -1;
    }

    public boolean isNear(float px, float py, float pz) {
        synchronized(items) {
            for (GameItem item : items) {
                float dx = px - item.x;
                float dy = py - item.y;
                float dz = pz - item.z;

                if (Math.sqrt(dx*dx + dy*dy + dz*dz) < 1.5f) {
                    return true;
                }
            }
        }
        return false;
    }

    public void draw(float[] viewMatrix, float[] projMatrix, int program, float time) {
        synchronized(items) { 
            if (items.isEmpty()) return;
            int posH = GLES20.glGetAttribLocation(program, "vPosition");
            int colH = GLES20.glGetUniformLocation(program, "vColor"); 
            int matH = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            int modeH = GLES20.glGetUniformLocation(program, "uMode");
            GLES20.glEnableVertexAttribArray(posH);
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glUniform1i(modeH, 1);
            for (GameItem item : items) {
                Matrix.setIdentityM(mModelMatrix, 0);
                float bob = (float)Math.sin(time * 3.0f + item.floatOffset) * 0.3f;
                float spin = time * 100f + (item.floatOffset * 50f);
                Matrix.translateM(mModelMatrix, 0, item.x, item.y + bob, item.z);
                if (item.type == 0) Matrix.rotateM(mModelMatrix, 0, spin, 0, 1, 0);
                else if (item.type == 1) Matrix.rotateM(mModelMatrix, 0, spin, 1, 1, 0); 
                else Matrix.rotateM(mModelMatrix, 0, spin, 0, 0, 1);
                Matrix.multiplyMM(mMVPMatrix, 0, viewMatrix, 0, mModelMatrix, 0);
                Matrix.multiplyMM(mMVPMatrix, 0, projMatrix, 0, mMVPMatrix, 0);
                GLES20.glUniformMatrix4fv(matH, 1, false, mMVPMatrix, 0);
                if (item.type == 0) GLES20.glUniform4fv(colH, 1, COL_GOLD, 0);
                else if (item.type == 1) GLES20.glUniform4fv(colH, 1, COL_HP, 0);
                else GLES20.glUniform4fv(colH, 1, COL_MANA, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
            }
            GLES20.glUniform1i(modeH, 0);
            GLES20.glDisableVertexAttribArray(posH);
        }
    }
}
