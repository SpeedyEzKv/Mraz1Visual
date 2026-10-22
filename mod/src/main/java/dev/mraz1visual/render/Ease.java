package dev.mraz1visual.render;

/** Функции плавности для эффектов (t 0..1 → 0..1). Всё считается по реальному времени кадра. */
public final class Ease {
    private Ease() {}

    public static float clamp(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float smooth(float t) {
        t = clamp(t);
        return t * t * (3 - 2 * t);
    }

    public static float outCubic(float t) {
        t = 1 - clamp(t);
        return 1 - t * t * t;
    }

    public static float outExpo(float t) {
        t = clamp(t);
        return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }

    public static float inOutSine(float t) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * clamp(t)));
    }

    /** С лёгким перелётом за 1 и возвратом. */
    public static float outBack(float t) {
        t = clamp(t);
        float c1 = 1.5f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Пружинистый выход (затухающие колебания). */
    public static float outElastic(float t) {
        t = clamp(t);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    /** Мягкое «появиться и угаснуть»: быстрый вход за in, долгий хвост. */
    public static float pulse(float t, float in) {
        t = clamp(t);
        return t < in ? smooth(t / in) : (float) Math.pow(1 - (t - in) / (1 - in), 1.6);
    }

    /** Экспоненциальное приближение значения к цели, одинаковое на любом FPS. rate — «скорость» в 1/с. */
    public static float approach(float v, float target, float rate, float dt) {
        return target + (v - target) * (float) Math.exp(-rate * dt);
    }

    public static double approach(double v, double target, double rate, double dt) {
        return target + (v - target) * Math.exp(-rate * dt);
    }
}
