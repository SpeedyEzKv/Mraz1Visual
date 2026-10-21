package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Общие «дорогие» фигуры для визуалов поверх {@link WorldDraw}: мягкое кольцо (тонкая кромка + ореол
 * в обе стороны), штриховое кольцо, стеклянный ромб с бликом на гранях. Цвета — уже готовые ARGB.
 */
public final class FxDraw {
    private FxDraw() {}

    /** Горизонтальное мягкое кольцо: яркая тонкая кромка, к центру и наружу — плавное затухание. */
    public static void softRing(WorldDraw d, double x, double y, double z, double r, double w, int c, int segs) {
        int clear = c & 0x00FFFFFF;
        d.ring(x, y, z, Math.max(0, r - w * 2.5), r - w / 2, segs, clear, Colors.mulAlpha(c, 0.55f));
        d.ring(x, y, z, r - w / 2, r + w / 2, segs, c, c);
        d.ring(x, y, z, r + w / 2, r + w * 3.5, segs, Colors.mulAlpha(c, 0.45f), clear);
    }

    /** Широкий ореол кольца в проходе свечения (вызывать между glowBegin/glowEnd). */
    public static void haloRing(WorldDraw d, double x, double y, double z, double r, double w, int c, int segs) {
        int clear = c & 0x00FFFFFF;
        d.ring(x, y, z, Math.max(0, r - w), r, segs, clear, c);
        d.ring(x, y, z, r, r + w, segs, c, clear);
    }

    /** Кольцо из count штрихов (fill — доля дуги, занятая штрихом), повёрнутое на rot. */
    public static void dashes(WorldDraw d, double x, double y, double z, double r, double w, int count, float fill, double rot, int c) {
        double step = Math.PI * 2 / count, arc = step * fill;
        int sub = 4;
        for (int i = 0; i < count; i++) {
            double a0 = rot + i * step;
            for (int s = 0; s < sub; s++) {
                double b0 = a0 + arc * s / sub, b1 = a0 + arc * (s + 1) / sub;
                // концы штриха — прозрачнее (скруглённый вид)
                float e0 = s == 0 ? 0.25f : 1f, e1 = s == sub - 1 ? 0.25f : 1f;
                int k0 = Colors.mulAlpha(c, e0), k1 = Colors.mulAlpha(c, e1);
                double ri = r - w / 2, ro = r + w / 2;
                double c0 = Math.cos(b0), s0 = Math.sin(b0), c1 = Math.cos(b1), s1 = Math.sin(b1);
                d.quad(x + c0 * ri, y, z + s0 * ri, x + c0 * ro, y, z + s0 * ro, x + c1 * ro, y, z + s1 * ro, x + c1 * ri, y, z + s1 * ri, k0, k0, k1, k1);
            }
        }
    }

    private static final double[] DX = new double[4], DZ = new double[4];

    /**
     * Стеклянный ромб (октаэдр): полупрозрачные грани, светлые рёбра; верхние грани светлее — «блик».
     * r — полуширина, h — полувысота, yaw — поворот вокруг вертикали.
     */
    public static void diamond(WorldDraw d, double x, double y, double z, double r, double h, double yaw, int fill, int edge) {
        for (int i = 0; i < 4; i++) {
            double a = yaw + i * Math.PI / 2;
            DX[i] = x + Math.cos(a) * r;
            DZ[i] = z + Math.sin(a) * r;
        }
        int top = Colors.lerp(fill, Colors.withAlpha(0xFFFFFF, fill >>> 24), 0.45f);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            // грань, повёрнутая к «свету» (сверху-сбоку), светлее
            float lit = 0.55f + 0.45f * (float) Math.max(0, Math.cos(yaw + i * Math.PI / 2 + Math.PI / 4 - 0.8));
            int up = Colors.lerp(fill, top, lit);
            d.tri(DX[i], y, DZ[i], DX[j], y, DZ[j], x, y + h, z, up, up, top);
            d.tri(DX[i], y, DZ[i], DX[j], y, DZ[j], x, y - h, z, fill, fill, Colors.mulAlpha(fill, 0.5f));
        }
        double t = Math.max(0.008, r * 0.05);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            d.line(DX[i], y, DZ[i], DX[j], y, DZ[j], t, edge);
            d.line(DX[i], y, DZ[i], x, y + h, z, t, edge);
            d.line(DX[i], y, DZ[i], x, y - h, z, t, Colors.mulAlpha(edge, 0.6f));
        }
    }
}
