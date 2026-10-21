package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Свои частицы: мягкие точки, искры (штрих по скорости), звёздочки и кольца. Летят, тормозят, падают,
 * плавно кружатся (гладкое поле турбулентности) и гаснут по ease-out. Один набор массивов на всё — без
 * аллокаций в кадре; рисуются в два прохода (обычный и свечение), пока есть живые. Лимит зависит от качества.
 */
public final class FxParticles {
    public static final byte DOT = 0, SPARK = 1, STAR = 2, RING = 3;
    private static final int MAX = 3000;
    private static final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
    private static final double[] vx = new double[MAX], vy = new double[MAX], vz = new double[MAX];
    private static final float[] life = new float[MAX], age = new float[MAX], size = new float[MAX], grav = new float[MAX];
    private static final float[] drag = new float[MAX], turb = new float[MAX], spin = new float[MAX];
    private static final int[] color = new int[MAX];
    private static final boolean[] core = new boolean[MAX], glow = new boolean[MAX];
    private static final byte[] shape = new byte[MAX];
    private static int count;
    private static long last;
    private static float time;

    private FxParticles() {}

    public static boolean isEmpty() {
        return count == 0;
    }

    private static int cap() {
        return Math.min(MAX, Math.round(1500 * Fx.quality()));
    }

    /** gravity: блоков/с² вниз; core — белая серединка для «искры». Мягкая точка в обычном проходе. */
    public static void spawn(double px, double py, double pz, double svx, double svy, double svz,
                             float lifeSec, float sz, int argb, float gravity, boolean withCore) {
        spawn(px, py, pz, svx, svy, svz, lifeSec, sz, argb, gravity, withCore, DOT, false, 0.35f, 0f);
    }

    /**
     * Полная форма. shape — {@link #DOT}/{@link #SPARK}/{@link #STAR}/{@link #RING}; additive — проход свечения;
     * keep — какая доля скорости остаётся за секунду (сопротивление воздуха); turbulence — сила плавного кружения.
     */
    public static boolean spawn(double px, double py, double pz, double svx, double svy, double svz,
                                float lifeSec, float sz, int argb, float gravity, boolean withCore,
                                byte form, boolean additive, float keep, float turbulence) {
        if (count >= cap()) return false;
        int i = count++;
        x[i] = px; y[i] = py; z[i] = pz;
        vx[i] = svx; vy[i] = svy; vz[i] = svz;
        life[i] = lifeSec; age[i] = 0; size[i] = sz; grav[i] = gravity;
        color[i] = argb; core[i] = withCore;
        shape[i] = form; glow[i] = additive; drag[i] = keep; turb[i] = turbulence;
        spin[i] = (float) (Math.random() * Math.PI * 2);
        return true;
    }

    static void render(WorldDraw d) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        time += dt;
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                remove(i);
                i--;
                continue;
            }
            vy[i] -= grav[i] * dt;
            if (turb[i] > 0) {
                // гладкое «вихревое» поле — частицы плывут, а не дрожат
                double px = x[i] * 1.3, py = y[i] * 1.3, pz = z[i] * 1.3;
                double nx = Math.sin(py + time * 1.1) + Math.sin(pz * 1.3 - time * 0.7);
                double ny = Math.sin(pz + time * 0.9) + Math.sin(px * 1.2 + time * 1.3);
                double nz = Math.sin(px - time * 1.2) + Math.sin(py * 1.4 + time * 0.8);
                float k = turb[i] * dt;
                vx[i] += nx * k; vy[i] += ny * k * 0.6; vz[i] += nz * k;
            }
            double dr = Math.pow(drag[i], dt);
            vx[i] *= dr; vy[i] *= dr; vz[i] *= dr;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            spin[i] += dt * 1.5f;
        }
        draw(d, false);
        d.glowBegin();
        draw(d, true);
        d.glowEnd();
    }

    private static void draw(WorldDraw d, boolean additive) {
        for (int i = 0; i < count; i++) {
            if (glow[i] != additive) continue;
            float t = age[i] / life[i];
            // быстрое мягкое появление и долгий ease-out хвост
            float a = t < 0.08f ? t / 0.08f : (float) Math.pow(1f - (t - 0.08f) / 0.92f, 1.7);
            int c = Colors.mulAlpha(color[i], a);
            float s = size[i];
            switch (shape[i]) {
                case SPARK -> {
                    double sp = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                    double len = Math.max(s * 1.5, Math.min(0.6, sp * 0.05));
                    double k = sp > 1e-4 ? len / sp : 0;
                    d.line(x[i], y[i], z[i], x[i] - vx[i] * k, y[i] - vy[i] * k, z[i] - vz[i] * k, s * 0.45, c);
                    if (core[i]) d.billboardDot(x[i], y[i], z[i], s * 0.5, Colors.mulAlpha(0xFFFFFFFF, a), 6);
                }
                case STAR -> {
                    float r = s * (1f - t * 0.3f);
                    for (int k = 0; k < 4; k++) d.billboardRay(x[i], y[i], z[i], spin[i] + k * Math.PI / 2, 0, r * 2.2, r * 0.45, c);
                    d.billboardDot(x[i], y[i], z[i], r * 0.9, Colors.mulAlpha(c, 0.6f), 8);
                    if (core[i]) d.billboardDot(x[i], y[i], z[i], r * 0.35, Colors.mulAlpha(0xFFFFFFFF, a), 6);
                }
                case RING -> {
                    float e = Ease.outCubic(t);
                    d.billboardRing(x[i], y[i], z[i], s * (0.3 + e), s * 0.12 * (1 - t * 0.5), Fx.segments(32), c);
                }
                default -> {
                    float r = s * (1f - t * 0.6f);
                    d.billboardDot(x[i], y[i], z[i], r, c, 8);
                    if (core[i]) d.billboardDot(x[i], y[i], z[i], r * 0.35f, Colors.mulAlpha(0xFFFFFFFF, a), 6);
                }
            }
        }
    }

    /** Убрать: переносим последний на место i. */
    private static void remove(int i) {
        int l = --count;
        x[i] = x[l]; y[i] = y[l]; z[i] = z[l]; vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
        life[i] = life[l]; age[i] = age[l]; size[i] = size[l]; grav[i] = grav[l]; color[i] = color[l]; core[i] = core[l];
        drag[i] = drag[l]; turb[i] = turb[l]; spin[i] = spin[l]; shape[i] = shape[l]; glow[i] = glow[l];
    }

    /** Сбросить все (смена мира/измерения — без «висящих» частиц на старых координатах). */
    public static void clear() {
        count = 0;
    }
}
