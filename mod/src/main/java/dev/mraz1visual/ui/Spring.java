package dev.mraz1visual.ui;

/**
 * Пружина (жёсткость + затухание) — для движений, которые должны «доезжать» живо, с лёгким перелётом,
 * как в iOS: индикатор вкладки, открытие окна, раскрытие карточек. Шаги считаются по реальному времени кадра
 * мелкими подшагами, поэтому на 60 и на 500 FPS движение одинаковое. «Уменьшить движение» — сразу в цель.
 */
public final class Spring {
    public float value, velocity, target;
    private final float stiffness, damping;

    public Spring(float stiffness, float damping, float initial) {
        this.stiffness = stiffness;
        this.damping = damping;
        this.value = this.target = initial;
    }

    /** Стандартная «мягкая» пружина интерфейса (небольшой перелёт, ~0,35 с). */
    public static Spring ui(float initial) {
        return new Spring(260f, 26f, initial);
    }

    public void snap(float v) {
        value = target = v;
        velocity = 0;
    }

    public float update(float dt) {
        if (!Anim.enabled || Anim.reduceMotion) {
            snap(target);
            return value;
        }
        dt = Math.min(dt, 0.1f);
        // полу-неявный Эйлер подшагами по ≤ 4 мс — устойчиво при любом FPS
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float a = stiffness * (target - value) - damping * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (Math.abs(target - value) < 0.001f && Math.abs(velocity) < 0.01f) snap(target);
        return value;
    }
}
