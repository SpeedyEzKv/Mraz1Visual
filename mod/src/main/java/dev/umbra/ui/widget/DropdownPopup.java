package dev.umbra.ui.widget;

import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Theme;

public final class DropdownPopup extends Popup {
    private static final int ITEM_H = 16;
    private final EnumSetting s;

    public DropdownPopup(EnumSetting s, int x, int y, int w) {
        this.s = s;
        setBounds(x, y, w, s.size() * ITEM_H + 4);
    }

    @Override
    public void render(Draw d, double mx, double my, float dt) {
        frame(d);
        for (int i = 0; i < s.size(); i++) {
            int iy = y + 2 + i * ITEM_H;
            boolean hov = Widget.hit(mx, my, x, iy, w, ITEM_H);
            if (hov) d.round(x + 2, iy, w - 4, ITEM_H, Theme.RADIUS_SM - 1, Theme.HOVER);
            boolean on = i == s.get();
            d.text(s.labels[i], x + 6, Draw.textY(iy, ITEM_H), on ? Theme.TEXT : hov ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
            if (on) d.circle(x + w - 8, iy + ITEM_H / 2, 3, Theme.accent());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!hit(mx, my)) return false;
        int i = (int) ((my - y - 2) / ITEM_H);
        if (i >= 0 && i < s.size()) {
            s.set(i);
            close();
        }
        return true;
    }
}
