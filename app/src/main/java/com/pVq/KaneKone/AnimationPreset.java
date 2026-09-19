package com.pVq.KaneKone.game;

public final class AnimationPreset {

    public static final int IDLE = 0;
    public static final int WALK = 1;
    public static final int RUN = 2;
    public static final int JUMP = 3;
    public static final int ATTACK = 4;
    public static final int HIT = 5;
    public static final int FALL = 6;

    /*
     * Range default di sini dibuat untuk satu timeline Prisma3D yang berisi
     * beberapa aksi dalam satu track.
     *
     * Format: startSec -> endSec
     * Kalau urutanmu beda, ubah angka ini saja. Sistem playback tetap sama.
     */
    public static final float[][] RANGES = new float[][]{
            {0.00f, 0.90f},   // idle
            {0.90f, 2.05f},   // walk
            {2.05f, 3.00f},   // run
            {3.00f, 3.85f},   // jump
            {3.85f, 5.35f},   // attack
            {5.35f, 6.20f},   // hit
            {6.20f, 7.20f}    // fall / recover
    };

    private AnimationPreset() {}
}
