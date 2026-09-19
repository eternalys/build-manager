package com.pVq.KaneKone.game;

public class IdleOriginal {

    // Parameter yang bisa kamu tweak
    private static final float BREATH_SPEED = 1.8f;
    private static final float BREATH_AMP = 1.2f;

    private static final float SWAY_SPEED = 0.6f;
    private static final float SWAY_AMP = 2.5f;

    private static final float HEAD_LOOK_SPEED = 0.4f;
    private static final float ARM_MICRO_SPEED = 1.1f;

    public static void apply(SkeletonAnimator animator, float time) {
        if (animator == null) return;

        float breath = (float) Math.sin(time * BREATH_SPEED) * BREATH_AMP;
        float sway   = (float) Math.sin(time * SWAY_SPEED) * SWAY_AMP;
        float micro  = (float) Math.sin(time * ARM_MICRO_SPEED);

        // ==================== BONE REFERENCES ====================
        int hip      = animator.findBone("hip", "pelvis", "root");
        int waist    = animator.findBone("waist", "spine", "Bone.001");
        int chest    = animator.findBone("chest", "spine2", "Bone.001");
        int head     = animator.findBone("head", "neck", "Bone.002");
        int lUpper   = animator.findBone("leftUpperArm", "l_upperarm", "Bone.003");
        int rUpper   = animator.findBone("rightUpperArm", "r_upperarm", "Bone.004");
        int lThigh   = animator.findBone("leftThigh", "l_thigh");
        int rThigh   = animator.findBone("rightThigh", "r_thigh");

        // ==================== IDLE ANIMATION ====================

        // 1. Hip Sway (weight shifting)
        if (hip >= 0) {
            animator.addRotation(hip, sway * 0.4f, 0, 1, 0);           // putar kiri-kanan
            animator.addRotation(hip, breath * 0.3f, 1, 0, 0);         // naik turun sedikit
        }

        // 2. Breathing (Chest + Waist)
        if (chest >= 0) {
            animator.addRotation(chest, breath * 0.6f, 1, 0, 0);
        }
        if (waist >= 0) {
            animator.addRotation(waist, breath * 0.4f, 1, 0, 0);
        }

        // 3. Head subtle movement (seperti orang hidup)
        if (head >= 0) {
            float headLook = (float) Math.sin(time * HEAD_LOOK_SPEED) * 3.5f;
            animator.addRotation(head, breath * 0.5f + headLook * 0.3f, 1, 0, 0);   // nod
            animator.addRotation(head, headLook * 0.6f, 0, 1, 0);                   // look left-right
        }

        // 4. Arm micro movement (tangan agak goyang natural)
        if (lUpper >= 0) {
            animator.addRotation(lUpper, micro * 4f + breath * 0.8f, 1, 0, 0);
            animator.addRotation(lUpper, sway * 0.8f, 0, 0, 1);   // sedikit ke samping
        }
        if (rUpper >= 0) {
            animator.addRotation(rUpper, -micro * 4f + breath * 0.8f, 1, 0, 0);
            animator.addRotation(rUpper, -sway * 0.8f, 0, 0, 1);
        }

        // 5. Thigh subtle weight shift
        if (lThigh >= 0) {
            animator.addRotation(lThigh, sway * -0.6f, 1, 0, 0);
        }
        if (rThigh >= 0) {
            animator.addRotation(rThigh, sway * 0.6f, 1, 0, 0);
        }
    }
}