package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Лента-след: точки пишутся каждый кадр (не по тикам — без «ступенек» на высоком FPS), между ними —
 * сплайн Catmull-Rom. Тает по длине и по времени, ширина сужается к хвосту, цвет — градиент палитры.
 * Виды: {@link #FACING} — лента, развёрнутая к камере; {@link #WALL} — вертикальная стенка высотой h;
 * {@link #FLAT} — горизонтальная лента (хорошо видна сзади и сверху — для следа за игроком).
 * Один объект — один след, без аллокаций в кадре.
 */
public final class Ribbon {
    public static final int FACING = 0, WALL = 1, FLAT = 2;

    private final int cap;
    private final double[] px, py, pz;
    private final float[] born;
    private int head, size;
    private float clock;
    /** Минимальный шаг между точками (блоки) — чтобы на 500 FPS не копились тысячи точек. */
    public double minStep = 0.06;
    /** Сколько живёт точка, с. */
    public float life = 0.6f;

    public Ribbon(int capacity) {
        cap = capacity;
        px = new double[cap];
        py = new double[cap];
        pz = new double[cap];
        born = new float[cap];
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    /** Добавить точку (раз в кадр). dt — время кадра; точки старше life отбрасываются. */
    public void push(double x, double y, double z, float dt) {
        clock += dt;
        while (size > 0 && clock - born[index(size - 1)] > life) size--;
        if (size > 0) {
            int h = index(0);
            double dx = x - px[h], dy = y - py[h], dz = z - pz[h];
            if (dx * dx + dy * dy + dz * dz > 64) size = 0; // телепорт — новая лента
            else if (size >= 2) {
                // шаг меньше minStep от последней закреплённой точки — голова просто следует за целью
                int f = index(1);
                double ex = x - px[f], ey = y - py[f], ez = z - pz[f];
                if (ex * ex + ey * ey + ez * ez < minStep * minStep) {
                    px[h] = x; py[h] = y; pz[h] = z;
                    born[h] = clock;
                    return;
                }
            } else if (dx * dx + dy * dy + dz * dz < minStep * minStep) return;
        }
        head = (head + 1) % cap;
        px[head] = x; py[head] = y; pz[head] = z;
        born[head] = clock;
        size = Math.min(size + 1, cap);
    }

    /** Только состарить (когда точку добавлять не нужно). */
    public void age(float dt) {
        clock += dt;
        while (size > 0 && clock - born[index(size - 1)] > life) size--;
    }

    /** i = 0 — самая свежая точка. */
    private int index(int i) {
        return ((head - i) % cap + cap) % cap;
    }

    private double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /**
     * Нарисовать. width — ширина у головы (или высота стенки для WALL), alpha — общая прозрачность,
     * preset/c1/c2 — палитра (t по длине: 0 — голова, 1 — хвост), headX… — текущая точка (по partialTick), чтобы голова не отставала.
     */
    public void draw(WorldDraw d, int mode, double width, float alpha, String preset, int c1, int c2,
                     double headX, double headY, double headZ) {
        if (size < 2 || alpha <= 0.002f) return;
        int sub = Math.max(1, Math.round(3 * Fx.quality()));
        int n = size;
        double lx = headX, ly = headY, lz = headZ, lw = width;
        int lc = Fx.color(preset, 0.05f, clock, c1, c2, alpha);
        float total = n - 1;
        float phase = clock * 0.6f;
        for (int i = 0; i < n - 1; i++) {
            int i0 = index(Math.max(0, i - 1)), i1 = index(i), i2 = index(i + 1), i3 = index(Math.min(n - 1, i + 2));
            for (int s = 1; s <= sub; s++) {
                double t = s / (double) sub;
                double x = cr(i == 0 ? headX : px[i0], i == 0 ? headX : px[i1], px[i2], px[i3], t);
                double y = cr(i == 0 ? headY : py[i0], i == 0 ? headY : py[i1], py[i2], py[i3], t);
                double z = cr(i == 0 ? headZ : pz[i0], i == 0 ? headZ : pz[i1], pz[i2], pz[i3], t);
                float f = (float) ((i + t) / total); // 0 голова … 1 хвост
                float age = (clock - born[i2]) / life;
                float a = alpha * (1 - f) * (1 - Ease.clamp(age) * 0.85f);
                double w = width * Math.pow(1 - f, 0.7);
                int c = Fx.color(preset, 0.05f + 0.9f * f, phase + f, c1, c2, a);
                if (mode == WALL) {
                    d.quad(lx, ly, lz, x, y, z, x, y + w, z, lx, ly + lw, lz, lc, c, Colors.mulAlpha(c, 0.15f), Colors.mulAlpha(lc, 0.15f));
                } else {
                    segment(d, lx, ly, lz, x, y, z, lw, w, lc, c, mode == FLAT);
                }
                lx = x; ly = y; lz = z; lw = w; lc = c;
            }
        }
    }

    /** Плоский отрезок ленты к камере: середина ярче, края прозрачные (мягкая лента без «ступенек»). */
    private void segment(WorldDraw d, double x0, double y0, double z0, double x1, double y1, double z1, double w0, double w1, int c0, int c1, boolean flat) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double vx = (x0 + x1) / 2 - d.camX(), vy = (y0 + y1) / 2 - d.camY(), vz = (z0 + z1) / 2 - d.camZ();
        if (flat) { vx = 0; vy = 1; vz = 0; } // поперёк — горизонтально
        double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9) return;
        nx /= len; ny /= len; nz /= len;
        double a0 = w0 / 2, a1 = w1 / 2;
        int e0 = c0 & 0x00FFFFFF, e1 = c1 & 0x00FFFFFF;
        // две половины: от края (прозрачно) к середине (цвет)
        d.quad(x0 - nx * a0, y0 - ny * a0, z0 - nz * a0, x0, y0, z0, x1, y1, z1, x1 - nx * a1, y1 - ny * a1, z1 - nz * a1, e0, c0, c1, e1);
        d.quad(x0, y0, z0, x0 + nx * a0, y0 + ny * a0, z0 + nz * a0, x1 + nx * a1, y1 + ny * a1, z1 + nz * a1, x1, y1, z1, c0, e0, e1, c1);
    }
}
