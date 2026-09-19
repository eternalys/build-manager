package com.pVq.KaneKone.game;

import android.opengl.Matrix;

public class SkeletonAnimator {

    public static final int MAX_BONES = 60;

    public static final int STATE_IDLE = AnimationPreset.IDLE;
    public static final int STATE_WALK = AnimationPreset.WALK;
    public static final int STATE_RUN = AnimationPreset.RUN;
    public static final int STATE_JUMP = AnimationPreset.JUMP;
    public static final int STATE_ATTACK = AnimationPreset.ATTACK;
    public static final int STATE_HIT = AnimationPreset.HIT;
    public static final int STATE_FALL = AnimationPreset.FALL;

    private GLBLoader.GLTFModel model;
    
    // Array untuk menyimpan klip animasi yang dipetakan ke setiap state
    private GLBLoader.GLTFAnimation[] animClips = new GLBLoader.GLTFAnimation[7];

    private int currentState = -1;
    private float stateTime = 0f;

    private int nodeCount = 0;
    private int jointCount = 0;

    private int[] parent;
    private float[] baseTranslation;
    private float[] baseRotation;
    private float[] baseScale;

    private float[] nodeTranslation;
    private float[] nodeRotation;
    private float[] nodeScale;

    private float[] localMatrix;
    private float[] worldMatrix;

    public final float[] finalBones = new float[MAX_BONES * 16];
    private final float[] tmpMat = new float[16];
    private final float[] rotMat = new float[16];

    public SkeletonAnimator() {
        resetFinalBones();
    }

    public void bindModel(GLBLoader.GLTFModel model) {
        this.model = model;
        this.stateTime = 0f;
        this.currentState = STATE_IDLE;

        // Reset semua klip
        for (int i = 0; i < 7; i++) animClips[i] = null;

        if (model == null || model.nodes == null) {
            clearState();
            resetFinalBones();
            return;
        }

        this.nodeCount = model.nodes.size();
        this.jointCount = (model.skinJoints != null) ? Math.min(model.skinJoints.length, MAX_BONES) : 0;

        this.parent = new int[nodeCount];
        this.baseTranslation = new float[nodeCount * 3];
        this.baseRotation = new float[nodeCount * 4];
        this.baseScale = new float[nodeCount * 3];

        this.nodeTranslation = new float[nodeCount * 3];
        this.nodeRotation = new float[nodeCount * 4];
        this.nodeScale = new float[nodeCount * 3];

        this.localMatrix = new float[nodeCount * 16];
        this.worldMatrix = new float[nodeCount * 16];

        // Backup Transform Bawaan
        for (int i = 0; i < nodeCount; i++) {
            GLBLoader.GLTFNode n = model.nodes.get(i);
            parent[i] = n.parent;

            baseTranslation[i * 3] = n.translation[0];
            baseTranslation[i * 3 + 1] = n.translation[1];
            baseTranslation[i * 3 + 2] = n.translation[2];

            baseRotation[i * 4] = n.rotation[0];
            baseRotation[i * 4 + 1] = n.rotation[1];
            baseRotation[i * 4 + 2] = n.rotation[2];
            baseRotation[i * 4 + 3] = n.rotation[3];

            baseScale[i * 3] = n.scale[0];
            baseScale[i * 3 + 1] = n.scale[1];
            baseScale[i * 3 + 2] = n.scale[2];
        }

        // --- PEMETAAN ANIMASI OTOMATIS DARI GLB ---
        if (model.animations != null) {
            for (GLBLoader.GLTFAnimation a : model.animations) {
                String animName = a.name.toLowerCase();
                
                if (animName.contains("idle")) animClips[STATE_IDLE] = a;
                else if (animName.contains("walk")) animClips[STATE_WALK] = a;
                else if (animName.contains("run")) animClips[STATE_RUN] = a;
                else if (animName.contains("jump")) animClips[STATE_JUMP] = a;
                else if (animName.contains("attack")) animClips[STATE_ATTACK] = a;
                else if (animName.contains("hit")) animClips[STATE_HIT] = a;
                else if (animName.contains("fall")) animClips[STATE_FALL] = a;
            }
            
            // Logika Fallback (Jika klip tertentu tidak ada di dalam file GLB)
            if (animClips[STATE_RUN] == null) animClips[STATE_RUN] = animClips[STATE_WALK];
            if (animClips[STATE_FALL] == null) animClips[STATE_FALL] = animClips[STATE_IDLE];
            if (animClips[STATE_HIT] == null) animClips[STATE_HIT] = animClips[STATE_IDLE];
        }

        resetFinalBones();
    }

    public void update(boolean moving, boolean running, boolean airborne, boolean jumping, boolean attacking, float dt) {
        if (dt < 0f) dt = 0f;

        if (model == null || nodeCount <= 0) {
            resetFinalBones();
            return;
        }

        stateTime += dt;

        // Tentukan state yang aktif
        int nextState;
        if (attacking) nextState = STATE_ATTACK;
        else if (jumping) nextState = STATE_JUMP;
        else if (airborne) nextState = STATE_FALL;
        else if (running) nextState = STATE_RUN;
        else if (moving) nextState = STATE_WALK;
        else nextState = STATE_IDLE;

        // Transisi State (Reset waktu ke 0 jika aksi berubah)
        if (nextState != currentState) {
            currentState = nextState;
            stateTime = 0f;
        }

        // Ambil klip sesuai dengan state
        GLBLoader.GLTFAnimation currentClip = animClips[currentState];

        if (currentClip != null) {
            // Speed up kalau running tapi pakai animasi walk
            float speedMul = (currentState == STATE_RUN && animClips[STATE_RUN] == animClips[STATE_WALK]) ? 1.35f : 1.0f;
            
            // Loop hanya untuk animasi pergerakan dasar, sisanya ditahan di frame terakhir
            boolean doLoop = (currentState == STATE_IDLE || currentState == STATE_WALK || currentState == STATE_RUN || currentState == STATE_FALL);
            
            updateFromClip(currentClip, speedMul, doLoop);
            buildMatrices(); 
        } else {
            // Jika klip GLB tidak ditemukan sama sekali, gunakan hitungan matematika darurat
            updateFallbackProcedural(moving, running, airborne, jumping, attacking, dt);
        }
    }

    private void updateFromClip(GLBLoader.GLTFAnimation currentClip, float speedMul, boolean isLooping) {
        // Kembalikan ke posisi tulang awal
        for (int i = 0; i < nodeCount; i++) {
            nodeTranslation[i * 3] = baseTranslation[i * 3];
            nodeTranslation[i * 3 + 1] = baseTranslation[i * 3 + 1];
            nodeTranslation[i * 3 + 2] = baseTranslation[i * 3 + 2];

            nodeRotation[i * 4] = baseRotation[i * 4];
            nodeRotation[i * 4 + 1] = baseRotation[i * 4 + 1];
            nodeRotation[i * 4 + 2] = baseRotation[i * 4 + 2];
            nodeRotation[i * 4 + 3] = baseRotation[i * 4 + 3];

            nodeScale[i * 3] = baseScale[i * 3];
            nodeScale[i * 3 + 1] = baseScale[i * 3 + 1];
            nodeScale[i * 3 + 2] = baseScale[i * 3 + 2];
        }

        float duration = (currentClip.duration > 0f) ? currentClip.duration : 1f;
        float localTime = stateTime * speedMul;

        // Logika Looping vs Tahan di Akhir Frame
        if (isLooping) {
            localTime = localTime % duration;
            if (localTime < 0f) localTime += duration;
        } else {
            if (localTime < 0f) localTime = 0f;
            if (localTime > duration) localTime = duration;
        }

        // Terapkan Keyframe dari GLB
        for (int c = 0; c < currentClip.channels.size(); c++) {
            GLBLoader.GLTFAnimation.Channel ch = currentClip.channels.get(c);

            if (ch.samplerIndex < 0 || ch.samplerIndex >= currentClip.samplers.size()) continue;
            GLBLoader.GLTFAnimation.Sampler s = currentClip.samplers.get(ch.samplerIndex);
            if (s == null || s.inputTimes == null || s.outputValues == null || s.inputTimes.length < 1) continue;

            int key = findKeyframeIndex(s.inputTimes, localTime);
            int next = Math.min(key + 1, s.inputTimes.length - 1);

            float t0 = s.inputTimes[key];
            float t1 = s.inputTimes[next];
            float alpha = (t1 <= t0) ? 0f : (localTime - t0) / (t1 - t0);

            if (alpha < 0f) alpha = 0f;
            if (alpha > 1f) alpha = 1f;

            int node = ch.targetNode;
            if (node < 0 || node >= nodeCount) continue;

            int comp = Math.max(1, s.components);
            int base0 = key * comp;
            int base1 = next * comp;

            if ("rotation".equals(ch.targetPath)) {
                float[] qa = new float[]{
                        getOrZero(s.outputValues, base0), getOrZero(s.outputValues, base0 + 1),
                        getOrZero(s.outputValues, base0 + 2), getOrZero(s.outputValues, base0 + 3)
                };
                float[] qb = new float[]{
                        getOrZero(s.outputValues, base1), getOrZero(s.outputValues, base1 + 1),
                        getOrZero(s.outputValues, base1 + 2), getOrZero(s.outputValues, base1 + 3)
                };

                float[] q = slerpQuat(qa, qb, alpha);
                nodeRotation[node * 4] = q[0];
                nodeRotation[node * 4 + 1] = q[1];
                nodeRotation[node * 4 + 2] = q[2];
                nodeRotation[node * 4 + 3] = q[3];

            } else if ("translation".equals(ch.targetPath)) {
                nodeTranslation[node * 3] = lerp(getOrZero(s.outputValues, base0), getOrZero(s.outputValues, base1), alpha);
                nodeTranslation[node * 3 + 1] = lerp(getOrZero(s.outputValues, base0 + 1), getOrZero(s.outputValues, base1 + 1), alpha);
                nodeTranslation[node * 3 + 2] = lerp(getOrZero(s.outputValues, base0 + 2), getOrZero(s.outputValues, base1 + 2), alpha);

            } else if ("scale".equals(ch.targetPath)) {
                nodeScale[node * 3] = lerp(getOrZero(s.outputValues, base0), getOrZero(s.outputValues, base1), alpha);
                nodeScale[node * 3 + 1] = lerp(getOrZero(s.outputValues, base0 + 1), getOrZero(s.outputValues, base1 + 1), alpha);
                nodeScale[node * 3 + 2] = lerp(getOrZero(s.outputValues, base0 + 2), getOrZero(s.outputValues, base1 + 2), alpha);
            }
        }
    }

    private void updateFallbackProcedural(boolean moving, boolean running, boolean airborne, boolean jumping, boolean attacking, float dt) {
        for (int i = 0; i < nodeCount; i++) {
            nodeTranslation[i * 3] = baseTranslation[i * 3];
            nodeTranslation[i * 3 + 1] = baseTranslation[i * 3 + 1];
            nodeTranslation[i * 3 + 2] = baseTranslation[i * 3 + 2];

            nodeRotation[i * 4] = baseRotation[i * 4];
            nodeRotation[i * 4 + 1] = baseRotation[i * 4 + 1];
            nodeRotation[i * 4 + 2] = baseRotation[i * 4 + 2];
            nodeRotation[i * 4 + 3] = baseRotation[i * 4 + 3];

            nodeScale[i * 3] = baseScale[i * 3];
            nodeScale[i * 3 + 1] = baseScale[i * 3 + 1];
            nodeScale[i * 3 + 2] = baseScale[i * 3 + 2];
        }

        float t = stateTime;
        float walk = moving ? 1f : 0f;
        float runMul = running ? 1.4f : 1f;

        int hip = findBone("hip", "pelvis", "root");
        int chest = findBone("Bone.001", "chest", "spine2");
        int lUpperArm = findBone("Bone.003", "l_upperarm", "left_upperarm");
        int rUpperArm = findBone("Bone.004", "r_upperarm", "right_upperarm");
        int lThigh = findBone("l_thigh", "left_thigh");
        int rThigh = findBone("r_thigh", "right_thigh");

        if (hip >= 0) {
            nodeTranslation[hip * 3 + 1] += (float) Math.sin(t * 6f) * 0.015f * walk;
            addRotation(hip, -4f * walk, 1, 0, 0);
        }
        if (chest >= 0) addRotation(chest, 2f * walk, 1, 0, 0);

        float swingL = (float) Math.sin(t * 6.3f * runMul);
        float swingR = (float) Math.sin(t * 6.3f * runMul + Math.PI);

        if (lUpperArm >= 0) addRotation(lUpperArm, -swingR * (runMul * 18f), 1, 0, 0);
        if (rUpperArm >= 0) addRotation(rUpperArm, -swingL * (runMul * 18f), 1, 0, 0);
        if (lThigh >= 0) addRotation(lThigh, swingL * (runMul * 24f), 1, 0, 0);
        if (rThigh >= 0) addRotation(rThigh, swingR * (runMul * 24f), 1, 0, 0);

        if (airborne || jumping) {
            if (chest >= 0) addRotation(chest, -8f, 1, 0, 0);
        } else if (attacking) {
            if (rUpperArm >= 0) addRotation(rUpperArm, -55f, 1, 0, 0);
            if (lUpperArm >= 0) addRotation(lUpperArm, 10f, 1, 0, 0);
            if (chest >= 0) addRotation(chest, -10f, 1, 0, 0);
        } else if (!moving && !running) {
            IdleOriginal.apply(this, t);
        }

        buildMatrices();
    }

    private void buildMatrices() {
        if (model == null || nodeCount <= 0) {
            resetFinalBones();
            return;
        }

        for (int i = 0; i < nodeCount; i++) {
            int off = i * 16;
            composeTRSInto(localMatrix, off,
                    nodeTranslation[i * 3], nodeTranslation[i * 3 + 1], nodeTranslation[i * 3 + 2],
                    nodeRotation[i * 4], nodeRotation[i * 4 + 1], nodeRotation[i * 4 + 2], nodeRotation[i * 4 + 3],
                    nodeScale[i * 3], nodeScale[i * 3 + 1], nodeScale[i * 3 + 2]);

            if (parent != null && parent[i] >= 0 && parent[i] < nodeCount) {
                int pOff = parent[i] * 16;
                Matrix.multiplyMM(tmpMat, 0, worldMatrix, pOff, localMatrix, off);
                System.arraycopy(tmpMat, 0, worldMatrix, off, 16);
            } else {
                System.arraycopy(localMatrix, off, worldMatrix, off, 16);
            }
        }

        resetFinalBones();

        if (model.skinJoints != null && model.inverseBindMatrices != null) {
            int limit = Math.min(Math.min(model.skinJoints.length, model.inverseBindMatrices.length / 16), MAX_BONES);
            for (int i = 0; i < limit; i++) {
                int nodeIndex = model.skinJoints[i];
                if (nodeIndex < 0 || nodeIndex >= nodeCount) continue;

                int nodeOff = nodeIndex * 16;
                int ibmOff = i * 16;
                Matrix.multiplyMM(tmpMat, 0, worldMatrix, nodeOff, model.inverseBindMatrices, ibmOff);
                System.arraycopy(tmpMat, 0, finalBones, ibmOff, 16);
            }
        }
    }

    public int findBone(String... keys) {
        if (model == null || model.jointNames == null) return -1;
        for (int i = 0; i < model.jointNames.length && i < MAX_BONES; i++) {
            String n = model.jointNames[i];
            if (n == null) continue;
            String low = n.toLowerCase();
            for (String k : keys) {
                if (low.contains(k.toLowerCase())) return i;
            }
        }
        return -1;
    }

    public void addRotation(int jointIndex, float angleDeg, float x, float y, float z) {
        if (jointIndex < 0 || jointIndex >= nodeCount) return;
        int off = jointIndex * 16;
        Matrix.setIdentityM(rotMat, 0);
        Matrix.rotateM(rotMat, 0, angleDeg, x, y, z);
        Matrix.multiplyMM(tmpMat, 0, localMatrix, off, rotMat, 0);
        System.arraycopy(tmpMat, 0, localMatrix, off, 16);
    }

    private void composeTRSInto(float[] out, int off, float tx, float ty, float tz, float qx, float qy, float qz, float qw, float sx, float sy, float sz) {
        float xx = qx * qx, yy = qy * qy, zz = qz * qz;
        float xy = qx * qy, xz = qx * qz, yz = qy * qz;
        float wx = qw * qx, wy = qw * qy, wz = qw * qz;

        out[off + 0]  = (1f - 2f * (yy + zz)) * sx;
        out[off + 1]  = (2f * (xy + wz)) * sx;
        out[off + 2]  = (2f * (xz - wy)) * sx;
        out[off + 3]  = 0f;

        out[off + 4]  = (2f * (xy - wz)) * sy;
        out[off + 5]  = (1f - 2f * (xx + zz)) * sy;
        out[off + 6]  = (2f * (yz + wx)) * sy;
        out[off + 7]  = 0f;

        out[off + 8]  = (2f * (xz + wy)) * sz;
        out[off + 9]  = (2f * (yz - wx)) * sz;
        out[off + 10] = (1f - 2f * (xx + yy)) * sz;
        out[off + 11] = 0f;

        out[off + 12] = tx;
        out[off + 13] = ty;
        out[off + 14] = tz;
        out[off + 15] = 1f;
    }

    private int findKeyframeIndex(float[] times, float t) {
        if (times == null || times.length < 2) return 0;
        if (t <= times[0]) return 0;
        for (int i = 0; i < times.length - 1; i++) {
            if (t >= times[i] && t <= times[i + 1]) return i;
        }
        return times.length - 2;
    }

    private float getOrZero(float[] arr, int index) {
        return (arr != null && index >= 0 && index < arr.length) ? arr[index] : 0f;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float[] slerpQuat(float[] a, float[] b, float t) {
        float ax = a[0], ay = a[1], az = a[2], aw = a[3];
        float bx = b[0], by = b[1], bz = b[2], bw = b[3];
        float dot = ax * bx + ay * by + az * bz + aw * bw;

        if (dot < 0f) {
            dot = -dot;
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }

        float scale0, scale1;
        if ((1f - dot) > 0.0001f) {
            double theta = Math.acos(dot);
            double sinTheta = Math.sin(theta);
            scale0 = (float) (Math.sin((1f - t) * theta) / sinTheta);
            scale1 = (float) (Math.sin(t * theta) / sinTheta);
        } else {
            scale0 = 1f - t;
            scale1 = t;
        }

        float[] out = new float[]{
                scale0 * ax + scale1 * bx,
                scale0 * ay + scale1 * by,
                scale0 * az + scale1 * bz,
                scale0 * aw + scale1 * bw
        };

        normalizeQuat(out);
        return out;
    }

    private void normalizeQuat(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len < 0.00001f) {
            q[0] = q[1] = q[2] = 0f;
            q[3] = 1f;
            return;
        }
        q[0] /= len;
        q[1] /= len;
        q[2] /= len;
        q[3] /= len;
    }

    private void clearState() {
        nodeCount = 0;
        jointCount = 0;
        parent = null;
        baseTranslation = null;
        baseRotation = null;
        baseScale = null;
        nodeTranslation = null;
        nodeRotation = null;
        nodeScale = null;
        localMatrix = null;
        worldMatrix = null;
    }

    private void resetFinalBones() {
        for (int i = 0; i < MAX_BONES; i++) {
            Matrix.setIdentityM(finalBones, i * 16);
        }
    }
}
