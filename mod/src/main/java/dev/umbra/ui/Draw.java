package dev.umbra.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Примитивы рисования окна. Все координаты — в единицах окна.
 * Скругления делаются полосками fill(): ноль шейдеров, ноль конфликтов с Iris.
 */
public final class Draw {
    public GuiGraphics g;
    public Font font;
    /** Сколько GUI-единиц Minecraft в одной единице окна. */
    public float scale = 1f;

    private static final int[][] INSETS = new int[33][];

    private static int[] insets(int r) {
        int[] in = INSETS[r];
        if (in == null) {
            in = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                in[i] = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            INSETS[r] = in;
        }
        return in;
    }

    public void begin(GuiGraphics g, Font font, float scale) {
        this.g = g;
        this.font = font;
        this.scale = scale;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
    }

    public void end() {
        g.pose().popMatrix();
    }

    public void rect(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public void round(int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r > 32) r = 32;
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int[] in = insets(r);
        for (int i = 0; i < r; i++) {
            int a = in[i];
            g.fill(x + a, y + i, x + w - a, y + i + 1, color);
            g.fill(x + a, y + h - 1 - i, x + w - a, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Скруглённый прямоугольник с обводкой в 1 единицу. */
    public void card(int x, int y, int w, int h, int r, int fill, int edge) {
        round(x, y, w, h, r, edge);
        round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public void circle(int cx, int cy, int d, int color) {
        round(cx - d / 2, cy - d / 2, d, d, d / 2, color);
    }

    public void gradientV(int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    public void hLine(int x, int y, int w, int color) { rect(x, y, w, 1, color); }

    public void vLine(int x, int y, int h, int color) { rect(x, y, 1, h, color); }

    public void text(String s, int x, int y, int color) {
        text(s, Fonts.Kind.TEXT, x, y, color);
    }

    public void text(String s, Fonts.Kind k, int x, int y, int color) {
        if ((color >>> 24) == 0 || s.isEmpty()) return;
        g.drawString(font, Fonts.of(s, k), x, y, color, false);
    }

    public void textRight(String s, int right, int y, int color) {
        text(s, right - width(s), y, color);
    }

    public void textCenter(String s, int cx, int y, int color) {
        text(s, cx - width(s) / 2, y, color);
    }

    /** Текст в масштабе k (крупные заголовки). */
    public void textScaled(String s, Fonts.Kind kind, int x, int y, float k, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(k, k);
        g.drawString(font, Fonts.of(s, kind), 0, 0, color, false);
        g.pose().popMatrix();
    }

    public int width(String s) { return Fonts.width(s); }

    public int width(String s, Fonts.Kind k) { return Fonts.width(s, k); }

    /** Вертикальный центр строки текста в блоке высотой h. */
    public static int textY(int y, int h) { return y + (h - Theme.FONT_H) / 2; }

    /** Тумблер: t = 0 выкл, 1 вкл (анимированное значение). */
    public void toggle(int x, int y, float t) {
        int track = Colors.lerp(Theme.TRACK, Theme.accentSoft(), t);
        round(x, y, Theme.SWITCH_W, Theme.SWITCH_H, Theme.SWITCH_H / 2, track);
        int knob = Theme.SWITCH_H - 4;
        int kx = x + 2 + Math.round((Theme.SWITCH_W - 4 - knob) * t);
        round(kx, y + 2, knob, knob, knob / 2, Colors.lerp(Theme.KNOB_OFF, Theme.KNOB_ON, t));
    }

    /** Шахматка под полупрозрачными цветами. */
    public void checker(int x, int y, int w, int h, int cell) {
        rect(x, y, w, h, 0xFF2A2A2A);
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = ((yy / cell) & 1) * cell; xx < w; xx += cell * 2) {
                rect(x + xx, y + yy, Math.min(cell, w - xx), Math.min(cell, h - yy), 0xFF4A4A4A);
            }
        }
    }

    /**
     * Обрезка. Ножницы ставим при снятом масштабе — в GUI-координатах Minecraft.
     * Так результат не зависит от того, применяет ли версия игры позу к ножницам.
     */
    public void clip(int x, int y, int w, int h) {
        g.pose().popMatrix();
        g.enableScissor((int) Math.floor(x * scale), (int) Math.floor(y * scale),
                (int) Math.ceil((x + w) * scale), (int) Math.ceil((y + h) * scale));
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
    }

    public void unclip() {
        g.disableScissor();
    }

    /** Обрезать строку под ширину с многоточием. Вызывать при построении, не в кадре. */
    public static String ellipsize(String s, int maxW) {
        if (Fonts.width(s) <= maxW) return s;
        String dots = "…";
        int dw = Fonts.width(dots);
        int end = s.length();
        while (end > 0 && Fonts.width(s.substring(0, end)) + dw > maxW) end--;
        return s.substring(0, end).stripTrailing() + dots;
    }
}
