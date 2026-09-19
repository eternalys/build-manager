package com.pVq.KaneKone.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class SkinnedMesh {

    public static final int STRIDE_FLOATS = 20;
    public static final int STRIDE_BYTES = STRIDE_FLOATS * 4;

    private FloatBuffer vertexBuffer;
    private int vertexCount;

    public SkinnedMesh(float[] rawData) {

        if (rawData == null) {
            throw new RuntimeException("rawData NULL");
        }

        if (rawData.length == 0) {
            throw new RuntimeException("rawData kosong");
        }

        if (rawData.length % STRIDE_FLOATS != 0) {
            throw new RuntimeException(
                    "Vertex corrupt. length=" + rawData.length
            );
        }

        vertexCount = rawData.length / STRIDE_FLOATS;

        ByteBuffer bb =
                ByteBuffer.allocateDirect(rawData.length * 4)
                        .order(ByteOrder.nativeOrder());

        vertexBuffer = bb.asFloatBuffer();

        vertexBuffer.put(rawData);

        vertexBuffer.position(0);
    }

    public FloatBuffer getBuffer() {

        if (vertexBuffer != null) {
            vertexBuffer.position(0);
        }

        return vertexBuffer;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public boolean isValid() {
        return vertexBuffer != null && vertexCount > 0;
    }

    public void destroy() {

        if (vertexBuffer != null) {
            vertexBuffer.clear();
        }

        vertexBuffer = null;
        vertexCount = 0;
    }
}