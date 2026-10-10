package dev.mraz1visual.ui.widget;

import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;

public final class ColorWidget extends SettingWidget {
    private static final int SW_W = 20, SW_H = 10;
    private final ColorSetting s;

    public ColorWidget(ColorSetting s, Host host) {
        super(s, host);
        this.s = s;
    }

    private int swX() { return right() - SW_W; }
    private int swY() { return y + (h - SW_H) / 2; }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        int sx = swX(), sy = swY();
        if (s.alpha() < 255) d.checker(sx, sy, SW_W, SW_H, 3);
        d.card(sx, sy, SW_W, SW_H, 2, s.get(), 0x30FFFFFF);
        d.textRight(s.hex(), sx - Theme.GAP, Draw.textY(y, h), Theme.TEXT_MUTED);
    }

    /** Клик по строке целиком открывает пипетку. */
    @Override
    protected boolean clickControl(double mx, double my) {
        float sc = host.scrollOf(this);
        host.openPopup(new ColorPopup(s, swX() + SW_W, Math.round(swY() - sc) + SW_H + 4));
        return true;
    }
}
