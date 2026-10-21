package dev.mraz1visual.models;

/**
 * Проигрыватель анимаций модели: текущая анимация и плавный переход (blend) из предыдущей.
 * Поза костей — в одном заранее выделенном массиве, в кадре без аллокаций.
 */
public final class AnimPlayer {
    private Anim cur, prev;
    private float t, prevT, blend = 1, blendTime = 0.25f;
    private float[] pose = new float[0];

    public Anim current() { return cur; }

    public float time() { return t; }

    /** Сменить анимацию с переходом за blendSec. Та же анимация не перезапускается, если restart = false. */
    public void play(Anim a, float blendSec, boolean restart) {
        if (a == cur && !restart) return;
        if (cur != null && blendSec > 0) {
            prev = cur;
            prevT = t;
            blend = 0;
            blendTime = blendSec;
        } else {
            prev = null;
            blend = 1;
        }
        cur = a;
        t = 0;
    }

    public void update(float dt) {
        t += dt;
        prevT += dt;
        if (blend < 1) blend = Math.min(1, blend + dt / blendTime);
        if (blend >= 1) prev = null;
    }

    /** Разовая анимация (once/hold) доиграла. */
    public boolean finished() {
        return cur != null && cur.finished(t);
    }

    /** Поза костей модели (на кость 9 чисел) или null — анимации нет. */
    public float[] pose(Mesh m) {
        if (cur == null && prev == null) return null;
        int n = m.bones.length * 9;
        if (pose.length < n) pose = new float[n];
        java.util.Arrays.fill(pose, 0, n, 0f);
        float w = smooth(blend);
        if (prev != null && w < 1) prev.apply(prevT, 1 - w, pose);
        if (cur != null) cur.apply(t, prev != null ? w : 1, pose);
        return pose;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
