package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Свои частицы-огоньки: летят, тормозят, падают и гаснут. Один массив на всё — без аллокаций в кадре.
 * Рисуются каждый кадр, пока есть живые; модули только вызывают spawn().
 */
public final class FxParticles {
    private static final int MAX = 1500;
    private static final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
    private static final double[] vx = new double[MAX], vy = new double[MAX], vz = new double[MAX];
    private static final float[] life = new float[MAX], age = new float[MAX], size = new float[MAX], grav = new float[MAX];
    private static final int[] color = new int[MAX];
    private static final boolean[] core = new boolean[MAX];
    private static int count;
    private static long last;

    private FxParticles() {}

    public static boolean isEmpty() {
        return count == 0;
    }

    /** gravity: блоков/с² вниз; core — белая серединка для «искры». */
    public static void spawn(double px, double py, double pz, double svx, double svy, double svz,
                             float lifeSec, float sz, int argb, float gravity, boolean withCore) {
        if (count >= MAX) return;
        int i = count++;
        x[i] = px; y[i] = py; z[i] = pz;
        vx[i] = svx; vy[i] = svy; vz[i] = svz;
        life[i] = lifeSec; age[i] = 0; size[i] = sz; grav[i] = gravity;
        color[i] = argb; core[i] = withCore;
    }

    static void render(WorldDraw d) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (count == 0) return;
        double drag = Math.pow(0.35, dt);
        for (int i = 0; i < count; i++) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                // убрать: переносим последний на место i
                int l = --count;
                x[i] = x[l]; y[i] = y[l]; z[i] = z[l]; vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
                life[i] = life[l]; age[i] = age[l]; size[i] = size[l]; grav[i] = grav[l]; color[i] = color[l]; core[i] = core[l];
                i--;
                continue;
            }
            vy[i] -= grav[i] * dt;
            vx[i] *= drag; vy[i] *= drag; vz[i] *= drag;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            float t = age[i] / life[i];
            float a = t < 0.1f ? t / 0.1f : 1f - (t - 0.1f) / 0.9f;
            float s = size[i] * (1f - t * 0.6f);
            d.billboardDot(x[i], y[i], z[i], s, Colors.mulAlpha(color[i], a), 8);
            if (core[i]) d.billboardDot(x[i], y[i], z[i], s * 0.35f, Colors.mulAlpha(0xFFFFFFFF, a), 6);
        }
    }
}
