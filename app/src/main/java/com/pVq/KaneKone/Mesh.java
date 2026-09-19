package com.pVq.KaneKone.game;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Mesh {
    private FloatBuffer vertexBuffer;
    private int vertexCount;
    private static final int STRIDE_BYTES = 10 * 4; // X,Y,Z (3) + NX,NY,NZ (3) + R,G,B,A (4)

    public Mesh(float[] rawData) {
        if (rawData == null || rawData.length == 0) {
            this.vertexCount = 0;
            return;
        }
        this.vertexCount = rawData.length / 10;
        ByteBuffer bb = ByteBuffer.allocateDirect(rawData.length * 4);
        bb.order(ByteOrder.nativeOrder());
        this.vertexBuffer = bb.asFloatBuffer();
        this.vertexBuffer.put(rawData);
        this.vertexBuffer.position(0);
    }

    public void draw(int program) {
        if (vertexCount == 0 || vertexBuffer == null) return;

        int aPos = GLES20.glGetAttribLocation(program, "vPosition");
        int aNor = GLES20.glGetAttribLocation(program, "vNormal");
        int aCol = GLES20.glGetAttribLocation(program, "av_Color");

        if (aPos != -1) {
            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);
        }
        if (aNor != -1) {
            vertexBuffer.position(3);
            GLES20.glEnableVertexAttribArray(aNor);
            GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);
        }
        if (aCol != -1) {
            vertexBuffer.position(6);
            GLES20.glEnableVertexAttribArray(aCol);
            GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        if(aPos != -1) GLES20.glDisableVertexAttribArray(aPos);
        if(aNor != -1) GLES20.glDisableVertexAttribArray(aNor);
        if(aCol != -1) GLES20.glDisableVertexAttribArray(aCol);
    }
}
