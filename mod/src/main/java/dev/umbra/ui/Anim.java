package dev.umbra.ui;

/** Плавное приближение к цели. Независимо от FPS. */
public final class Anim {
    private Anim() {}

    public static boolean enabled = true;

    public static float to(float current, float target, float dt) {
        if (!enabled) return target;
        float k = 1f - (float) Math.exp(-Theme.ANIM_SPEED * dt);
        float v = current + (target - current) * k;
        return Math.abs(target - v) < 0.002f ? target : v;
    }
}
