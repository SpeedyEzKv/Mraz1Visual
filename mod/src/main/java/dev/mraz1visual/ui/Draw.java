package dev.mraz1visual.ui;

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
        if (!Sdf.broken) {
            Sdf.fill(g, x, y, w, h, r, color);
            return;
        }
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
        if (!Sdf.broken) {
            Sdf.fill(g, x, y, w, h, r, fill);
            Sdf.shape(g, x, y, w, h, r, Sdf.RING, 1f, edge, edge);
            return;
        }
        round(x, y, w, h, r, edge);
        round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    // ── дробные примитивы (для превью и анимаций) ─────────────

    /** Прямоугольник с точностью 1/4 единицы. */
    public void rectF(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().scale(0.25f, 0.25f);
        int x0 = Math.round(x * 4), y0 = Math.round(y * 4);
        int x1 = Math.max(x0 + 1, Math.round((x + w) * 4)), y1 = Math.max(y0 + 1, Math.round((y + h) * 4));
        g.fill(x0, y0, x1, y1, color);
        g.pose().popMatrix();
    }

    /** Отрезок заданной толщины. */
    public void line(float x0, float y0, float x1, float y1, float t, int color) {
        if ((color >>> 24) == 0) return;
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.pose().scale(0.25f, 0.25f);
        int ht = Math.max(1, Math.round(t * 2));
        g.fill(0, -ht, Math.round(len * 4), ht, color);
        g.pose().popMatrix();
    }

    /** Окружность-кольцо. */
    public void ring(float cx, float cy, float r, float t, int color) {
        if (!Sdf.broken) {
            Sdf.shape(g, cx - r - t / 2, cy - r - t / 2, 2 * r + t, 2 * r + t, r + t / 2, Sdf.RING, t, color, color);
            return;
        }
        int seg = Math.max(12, Math.min(64, Math.round(r * 2.2f)));
        float px = cx + r, py = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float nx = cx + (float) Math.cos(a) * r, ny = cy + (float) Math.sin(a) * r;
            line(px, py, nx, ny, t, color);
            px = nx;
            py = ny;
        }
    }

    /** Эллипс-кольцо (для «кругов на земле»). */
    public void ellipseRing(float cx, float cy, float rx, float ry, float t, int color) {
        int seg = Math.max(16, Math.min(72, Math.round(rx * 2)));
        float px = cx + rx, py = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float nx = cx + (float) Math.cos(a) * rx, ny = cy + (float) Math.sin(a) * ry;
            line(px, py, nx, ny, t, color);
            px = nx;
            py = ny;
        }
    }

    /** Закрашенный круг с точностью 1/4 единицы. */
    public void disc(float cx, float cy, float r, int color) {
        if (r <= 0) return;
        if (!Sdf.broken) {
            Sdf.fill(g, cx - r, cy - r, 2 * r, 2 * r, r, color);
            return;
        }
        float step = 0.25f;
        for (float y = -r; y < r; y += step) {
            float yy = y + step / 2;
            float hw = (float) Math.sqrt(Math.max(0, r * r - yy * yy));
            rectF(cx - hw, cy + y, hw * 2, step, color);
        }
    }

    /** Иконка из атласа {@link Icons}. */
    public void icon(int index, int x, int y, int size, int color) {
        Icons.draw(g, index, x, y, size, color);
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
        Fonts.draw(g, s, k, x, y, color);
    }

    public void textRight(String s, int right, int y, int color) {
        text(s, right - width(s), y, color);
    }

    public void textCenter(String s, int cx, int y, int color) {
        text(s, cx - width(s) / 2, y, color);
    }

    /** Текст в масштабе k (крупные заголовки). */
    public void textScaled(String s, Fonts.Kind kind, int x, int y, float k, int color) {
        Fonts.draw(g, s, kind, x, y, k, color);
    }

    public int width(String s) { return Fonts.width(s); }

    public int width(String s, Fonts.Kind k) { return Fonts.width(s, k); }

    /** Вертикальный центр строки текста в блоке высотой h. */
    public static int textY(int y, int h) { return y + (h - Theme.FONT_H) / 2; }

    /** Тумблер: t = 0 выкл, 1 вкл (анимированное значение). */
    public void toggle(int x, int y, float t) {
        int track = Colors.lerp(Theme.TRACK, Theme.accent(), t);
        round(x, y, Theme.SWITCH_W, Theme.SWITCH_H, Theme.SWITCH_H / 2, track);
        int knob = Theme.SWITCH_H - 4;
        int kx = x + 2 + Math.round((Theme.SWITCH_W - 4 - knob) * t);
        round(kx, y + 2, knob, knob, knob / 2, Colors.lerp(Theme.KNOB_OFF, Theme.KNOB_ON, t));
    }

    // ── жидкое стекло ────────────────────────────────────────

    /**
     * Стеклянная панель: полупрозрачная заливка, мягкий блик сверху и светлый кант
     * (ярче сверху — как свет на стекле). Размытый фон под ней рисуется отдельно (см. WallpaperPlayer.glass).
     */
    public void glass(int x, int y, int w, int h, int r, int tint, float strength) {
        boolean light = Theme.isLight();
        if (!Sdf.broken) {
            // заливка, блик сверху (затухает к середине) и кромка с градиентом — три элемента вместо сотен полосок
            Sdf.fill(g, x, y, w, h, r, tint);
            int sheenTop = Colors.withAlpha(0xFFFFFF, Math.round((light ? 60 : 18) * strength));
            Sdf.shape(g, x + 1, y + 1, w - 2, Math.max(2, h * 0.55f), Math.max(0, r - 1), Sdf.FILL, 0, sheenTop, sheenTop & 0x00FFFFFF);
            int top = Colors.withAlpha(0xFFFFFF, Math.round((light ? 200 : 90) * strength));
            int bottom = Colors.withAlpha(0xFFFFFF, Math.round((light ? 70 : 20) * strength));
            Sdf.shape(g, x, y, w, h, r, Sdf.RING, 1f, top, bottom);
            return;
        }
        round(x, y, w, h, r, tint);
        int sheenH = Math.max(4, Math.min(h - 2, Math.round(h * 0.45f)));
        int sheen = Colors.withAlpha(0xFFFFFF, Math.round((light ? 70 : 20) * strength));
        int rr = Math.min(r, Math.min(w, h) / 2);
        if (sheenH > rr) gradientV(x + Math.max(1, rr / 2), y + 1, w - 2 * Math.max(1, rr / 2), sheenH, sheen, sheen & 0x00FFFFFF);
        int top = Colors.withAlpha(0xFFFFFF, Math.round((light ? 200 : 64) * strength));
        int bottom = Colors.withAlpha(0xFFFFFF, Math.round((light ? 90 : 14) * strength));
        rim(x, y, w, h, r, top, bottom);
    }

    /** Кант по скруглённому контуру: сверху цвет top, снизу bottom, по бокам — переход. */
    public void rim(int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 2 || h <= 2) return;
        if (!Sdf.broken) {
            Sdf.shape(g, x, y, w, h, r, Sdf.RING, 1f, top, bottom);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r > 32) r = 32;
        if (r <= 0) {
            hLine(x, y, w, top);
            hLine(x, y + h - 1, w, bottom);
            gradientV(x, y + 1, 1, h - 2, top, bottom);
            gradientV(x + w - 1, y + 1, 1, h - 2, top, bottom);
            return;
        }
        int[] in = insets(r);
        hLine(x + in[0], y, w - 2 * in[0], top);
        hLine(x + in[0], y + h - 1, w - 2 * in[0], bottom);
        for (int i = 1; i < r; i++) {
            int a = in[i], prev = in[i - 1];
            int len = Math.max(1, prev - a);
            rect(x + a, y + i, len, 1, top);
            rect(x + w - a - len, y + i, len, 1, top);
            rect(x + a, y + h - 1 - i, len, 1, bottom);
            rect(x + w - a - len, y + h - 1 - i, len, 1, bottom);
        }
        gradientV(x, y + r, 1, h - 2 * r, top, bottom);
        gradientV(x + w - 1, y + r, 1, h - 2 * r, top, bottom);
    }

    /** Мягкая тень под фигурой: расползается наружу на soft единиц. */
    public void shadow(int x, int y, int w, int h, int r, float soft, int color) {
        if (!Sdf.broken) Sdf.shape(g, x, y, w, h, r, Sdf.SHADOW, soft, color, color);
        else round(x - 2, y, w + 4, h + 4, r + 2, Colors.mulAlpha(color, 0.5f));
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
