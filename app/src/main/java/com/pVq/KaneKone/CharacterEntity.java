package com.pVq.KaneKone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

public class CharacterEntity {

    public static final int STATE_IDLE = AnimationPreset.IDLE;
    public static final int STATE_WALK = AnimationPreset.WALK;
    public static final int STATE_RUN = AnimationPreset.RUN;
    public static final int STATE_JUMP = AnimationPreset.JUMP;
    public static final int STATE_ATTACK = AnimationPreset.ATTACK;
    public static final int STATE_HIT = AnimationPreset.HIT;
    public static final int STATE_FALL = AnimationPreset.FALL;

    public final GLBLoader.GLTFModel model;
    public final SkeletonAnimator animator;

    public float x, y, z;
    public float rotationY;
    public float scale = 1.15f;
    public boolean visible = true;

    public boolean aiEnabled = false;
    public float moveSpeed = 1.5f;

    public int state = STATE_IDLE;
    public float targetX, targetZ;

    public CharacterEntity(GLBLoader.GLTFModel model) {
        this.model = model;
        this.animator = new SkeletonAnimator();
        this.animator.bindModel(model);
    }

    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetX = x;
        this.targetZ = z;
    }

    public void setAI(boolean enabled) {
        aiEnabled = enabled;
    }

    public void update(boolean moving, boolean running, boolean airborne, boolean jumping, boolean attacking, float dt) {
        if (aiEnabled) {
            updateAI(dt);
            moving = Math.abs(targetX - x) > 0.1f || Math.abs(targetZ - z) > 0.1f;
        }

        if (attacking) state = STATE_ATTACK;
        else if (jumping) state = STATE_JUMP;
        else if (airborne) state = STATE_FALL;
        else if (running) state = STATE_RUN;
        else if (moving) state = STATE_WALK;
        else state = STATE_IDLE;

        // animator.setState() DIBUANG agar waktu animasi maju terus
        animator.update(moving, running, airborne, jumping, attacking, dt);
    }

    private void updateAI(float dt) {
        float dx = targetX - x;
        float dz = targetZ - z;
        float d2 = dx * dx + dz * dz;

        if (d2 < 0.25f) {
            targetX = x + ((float)Math.random() * 8f - 4f);
            targetZ = z + ((float)Math.random() * 8f - 4f);
            return;
        }

        float d = (float)Math.sqrt(d2);
        float step = moveSpeed * dt;
        x += (dx / d) * step;
        z += (dz / d) * step;
        float desired = (float)Math.toDegrees(Math.atan2(dx, dz));
        rotationY = Gods.rotLerp(rotationY, desired, Math.min(1f, dt * 8f));
    }

    public void draw(int program, float[] viewMatrix, float[] projectionMatrix) {
        if (!visible || model == null || model.mesh == null || !model.mesh.isValid()) return;

        float[] modelMatrix = new float[16];
        float[] vp = new float[16];
        float[] mvp = new float[16];

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, x, y, z);
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f);
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale);

        Matrix.multiplyMM(vp, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, vp, 0);

        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uType"), 2.0f);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModelMatrix"), 1, false, modelMatrix, 0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_BoneMatrices"), SkeletonAnimator.MAX_BONES, false, animator.finalBones, 0);

        FloatBuffer b = model.mesh.getBuffer();
        if (b == null) return;

        int s = SkinnedMesh.STRIDE_FLOATS * 4;
        int aPos = GLES20.glGetAttribLocation(program, "vPosition");
        int aNor = GLES20.glGetAttribLocation(program, "vNormal");
        int aCol = GLES20.glGetAttribLocation(program, "av_Color");
        int aUV = GLES20.glGetAttribLocation(program, "a_UV");
        int aW = GLES20.glGetAttribLocation(program, "a_Weights");
        int aJ = GLES20.glGetAttribLocation(program, "a_JointIds");

        b.position(0); if (aPos != -1) { GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, s, b); }
        b.position(3); if (aNor != -1) { GLES20.glEnableVertexAttribArray(aNor); GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, s, b); }
        b.position(6); if (aCol != -1) { GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, s, b); }
        b.position(10); if (aUV != -1) { GLES20.glEnableVertexAttribArray(aUV); GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, s, b); }
        b.position(12); if (aW != -1) { GLES20.glEnableVertexAttribArray(aW); GLES20.glVertexAttribPointer(aW, 4, GLES20.GL_FLOAT, false, s, b); }
        b.position(16); if (aJ != -1) { GLES20.glEnableVertexAttribArray(aJ); GLES20.glVertexAttribPointer(aJ, 4, GLES20.GL_FLOAT, false, s, b); }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, model.mesh.getVertexCount());

        if (aPos != -1) GLES20.glDisableVertexAttribArray(aPos);
        if (aNor != -1) GLES20.glDisableVertexAttribArray(aNor);
        if (aCol != -1) GLES20.glDisableVertexAttribArray(aCol);
        if (aUV != -1) GLES20.glDisableVertexAttribArray(aUV);
        if (aW != -1) GLES20.glDisableVertexAttribArray(aW);
        if (aJ != -1) GLES20.glDisableVertexAttribArray(aJ);
    }
}
