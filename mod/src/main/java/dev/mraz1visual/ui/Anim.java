package dev.mraz1visual.ui;

/** Плавное приближение к цели. Независимо от FPS. */
public final class Anim {
    private Anim() {}

    public static boolean enabled = true;
    /** «Уменьшить движение»: пружины и сдвиги — сразу в цель, остаются только плавные проявления. */
    public static boolean reduceMotion;

    public static float to(float current, float target, float dt) {
        if (!enabled) return target;
        float k = 1f - (float) Math.exp(-Theme.ANIM_SPEED * dt);
        float v = current + (target - current) * k;
        return Math.abs(target - v) < 0.002f ? target : v;
    }
}
