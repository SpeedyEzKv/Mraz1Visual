package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Палитра и общие примитивы нового HUD: акцентный градиент #8B6CFF → #4FD1FF, красный, жёлтый, зелёный,
 * стеклянная плашка со скруглением и тонкой светлой обводкой.
 */
public final class HudStyle {
    private HudStyle() {}

    public static final int GRAD_A = 0xFF8B6CFF, GRAD_B = 0xFF4FD1FF;
    public static final int RED = 0xFFFF5A6E, YELLOW = 0xFFFFD34D, GREEN = 0xFF4ADE80;
    public static final int TEXT = 0xFFF2F2F7, TEXT_DIM = 0xFF9A9AAE;

    /** Цвет градиента в точке t (0 — фиолетовый, 1 — голубой). */
    public static int grad(float t) {
        return Colors.lerp(GRAD_A, GRAD_B, Math.max(0, Math.min(1, t)));
    }

    /** Стеклянная плашка: мягкая тень, тёмное стекло, светлая кромка. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float r, float alpha) {
        Sdf.shape(g, x, y + 1.5f, w, h, r, Sdf.SHADOW, 8, Colors.withAlpha(0, Math.round(70 * alpha)), Colors.withAlpha(0, Math.round(70 * alpha)));
        Sdf.shape(g, x, y, w, h, r, Sdf.GLASS, 6f, Colors.withAlpha(0x0C0C12, Math.round(0xD0 * alpha)), Colors.withAlpha(0x0C0C12, Math.round(0xD0 * alpha)));
        Sdf.shape(g, x, y, w, h, r, Sdf.RING, 0.6f, Colors.withAlpha(0xFFFFFF, Math.round(0x30 * alpha)), Colors.withAlpha(0xFFFFFF, Math.round(0x12 * alpha)));
    }

    /** Полоса с градиентной заливкой f (0..1) по горизонтали. */
    public static void gradBar(GuiGraphics g, float x, float y, float w, float h, float f, int from, int to, float alpha) {
        Sdf.fill(g, x, y, w, h, h / 2, Colors.withAlpha(0xFFFFFF, Math.round(0x22 * alpha)));
        float fw = w * Math.max(0, Math.min(1, f));
        if (fw < 0.5f) return;
        // капсула цвета начала, поверх — прямые сегменты градиента между скруглёнными краями
        Sdf.fill(g, x, y, fw, h, h / 2, Colors.mulAlpha(Colors.lerp(from, to, f), alpha));
        Sdf.fill(g, x, y, Math.min(fw, h), h, h / 2, Colors.mulAlpha(from, alpha));
        float inner = fw - h;
        if (inner <= 0) return;
        int n = Math.max(1, Math.min(10, Math.round(inner / 8)));
        float sw = inner / n;
        for (int i = 0; i < n; i++) {
            int c = Colors.mulAlpha(Colors.lerp(from, to, (i + 0.5f) / n * f), alpha);
            g.fill(Math.round(x + h / 2 + i * sw), Math.round(y), Math.round(x + h / 2 + (i + 1) * sw), Math.round(y + h), c);
        }
    }
}
