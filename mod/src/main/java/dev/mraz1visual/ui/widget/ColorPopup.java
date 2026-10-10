package dev.mraz1visual.ui.widget;

import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;

/**
 * Пипетка: квадрат насыщенность/яркость, полоса оттенка, полоса прозрачности.
 * Рисуется только пока открыта.
 */
public final class ColorPopup extends Popup {
    private static final int P = 6, SQ = 80, BAR = 8, INFO_H = 14;
    private static final int[] HUE_KEYS = {0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFF0000};

    private final ColorSetting s;
    private final float[] hsv = new float[3];
    private float alpha;
    private int drag; // 0 нет, 1 квадрат, 2 оттенок, 3 альфа

    public ColorPopup(ColorSetting s, int rightX, int y) {
        this.s = s;
        int w = P + SQ + P + BAR + P + BAR + P;
        int h = P + SQ + P + INFO_H;
        setBounds(rightX - w, y, w, h);
        Colors.toHsv(s.get(), hsv);
        alpha = s.alpha() / 255f;
    }

    private int sqX() { return x + P; }
    private int sqY() { return y + P; }
    private int hueX() { return sqX() + SQ + P; }
    private int alX() { return hueX() + BAR + P; }

    @Override
    public void render(Draw d, double mx, double my, float dt) {
        frame(d);
        int sx = sqX(), sy = sqY();
        // квадрат: по X — насыщенность, по Y — яркость; столбцы по 2 единицы
        for (int i = 0; i < SQ; i += 2) {
            int top = 0xFF000000 | Colors.hsv(hsv[0], i / (float) (SQ - 1), 1);
            d.gradientV(sx + i, sy, 2, SQ, top, 0xFF000000);
        }
        int px = sx + Math.round(hsv[1] * (SQ - 1)), py = sy + Math.round((1 - hsv[2]) * (SQ - 1));
        d.circle(px, py, 6, 0xFFFFFFFF);
        d.circle(px, py, 4, 0xFF000000 | Colors.hsv(hsv[0], hsv[1], hsv[2]));

        // оттенок
        int hx = hueX();
        int seg = SQ / 6;
        for (int i = 0; i < 6; i++) {
            int hh = i == 5 ? SQ - seg * 5 : seg;
            d.gradientV(hx, sy + i * seg, BAR, hh, 0xFF000000 | HUE_KEYS[i], 0xFF000000 | HUE_KEYS[i + 1]);
        }
        d.rect(hx - 1, sy + Math.round(hsv[0] * (SQ - 1)) - 1, BAR + 2, 2, 0xFFFFFFFF);

        // прозрачность
        int ax = alX();
        d.checker(ax, sy, BAR, SQ, 4);
        int rgb = Colors.hsv(hsv[0], hsv[1], hsv[2]);
        d.gradientV(ax, sy, BAR, SQ, 0xFF000000 | rgb, rgb);
        d.rect(ax - 1, sy + Math.round((1 - alpha) * (SQ - 1)) - 1, BAR + 2, 2, 0xFFFFFFFF);

        // подпись
        d.text(s.hex(), sx, sy + SQ + P + 2, Theme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!hit(mx, my)) return false;
        if (button == 1) {
            s.reset();
            Colors.toHsv(s.get(), hsv);
            alpha = s.alpha() / 255f;
            return true;
        }
        int sy = sqY();
        if (Widget.hit(mx, my, sqX() - 2, sy - 2, SQ + 4, SQ + 4)) drag = 1;
        else if (Widget.hit(mx, my, hueX() - 2, sy - 2, BAR + 4, SQ + 4)) drag = 2;
        else if (Widget.hit(mx, my, alX() - 2, sy - 2, BAR + 4, SQ + 4)) drag = 3;
        else drag = 0;
        mouseDragged(mx, my);
        return true;
    }

    @Override
    public void mouseDragged(double mx, double my) {
        if (drag == 0) return;
        float fy = clamp01((float) (my - sqY()) / (SQ - 1));
        switch (drag) {
            case 1 -> {
                hsv[1] = clamp01((float) (mx - sqX()) / (SQ - 1));
                hsv[2] = 1 - fy;
            }
            case 2 -> hsv[0] = Math.min(fy, 0.999f);
            case 3 -> alpha = 1 - fy;
            default -> {}
        }
        s.set(Colors.withAlpha(Colors.hsv(hsv[0], hsv[1], hsv[2]), Math.round(alpha * 255)));
    }

    @Override
    public void mouseReleased(double mx, double my) {
        drag = 0;
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
