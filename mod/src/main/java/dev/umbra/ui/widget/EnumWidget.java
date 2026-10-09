package dev.umbra.ui.widget;

import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import net.minecraft.client.gui.Font;

/**
 * До трёх вариантов — сегменты (выбор в один клик).
 * Больше — выпадающий список.
 */
public final class EnumWidget extends SettingWidget {
    private static final int SEG_PAD = 6;
    private final EnumSetting s;
    private final boolean segmented;
    private final int[] segW;
    private final int totalW;
    private float selX = -1;
    private float btnHover;

    public EnumWidget(EnumSetting s, Host host, Font font) {
        super(s, host);
        this.s = s;
        this.segmented = s.size() <= 3;
        this.segW = new int[s.size()];
        int sum = 0, max = 0;
        for (int i = 0; i < s.size(); i++) {
            segW[i] = Fonts.width(s.labels[i]) + SEG_PAD * 2;
            sum += segW[i];
            max = Math.max(max, Fonts.width(s.labels[i]));
        }
        this.totalW = segmented ? sum + 4 : max + 22;
    }

    private int boxX() { return right() - totalW; }
    private int boxY() { return y + (h - Theme.FIELD_H) / 2; }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        int bx = boxX(), by = boxY();
        if (segmented) {
            d.round(bx, by, totalW, Theme.FIELD_H, Theme.RADIUS_SM, Theme.FIELD);
            int sx = bx + 2, target = 0;
            for (int i = 0; i < s.size(); i++) {
                if (i == s.get()) target = sx;
                sx += segW[i];
            }
            selX = selX < 0 ? target : Anim.to(selX, target, dt);
            d.round(Math.round(selX), by + 2, segW[s.get()], Theme.FIELD_H - 4, Theme.RADIUS_SM - 1, Theme.SELECTED);
            sx = bx + 2;
            for (int i = 0; i < s.size(); i++) {
                boolean on = i == s.get();
                boolean hov = Widget.hit(mx, my, sx, by, segW[i], Theme.FIELD_H);
                d.text(s.labels[i], sx + SEG_PAD, Draw.textY(by, Theme.FIELD_H),
                        on ? Theme.TEXT : hov ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
                sx += segW[i];
            }
        } else {
            btnHover = Anim.to(btnHover, Widget.hit(mx, my, bx, by, totalW, Theme.FIELD_H) ? 1 : 0, dt);
            d.round(bx, by, totalW, Theme.FIELD_H, Theme.RADIUS_SM, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, btnHover));
            d.text(s.label(), bx + SEG_PAD, Draw.textY(by, Theme.FIELD_H), Theme.TEXT);
            d.text("▾", bx + totalW - 11, Draw.textY(by, Theme.FIELD_H), Theme.TEXT_MUTED);
        }
    }

    @Override
    protected boolean clickControl(double mx, double my) {
        int bx = boxX(), by = boxY();
        if (segmented) {
            int sx = bx + 2;
            for (int i = 0; i < s.size(); i++) {
                if (Widget.hit(mx, my, sx, by, segW[i], Theme.FIELD_H)) {
                    s.set(i);
                    return true;
                }
                sx += segW[i];
            }
            return false;
        }
        if (!Widget.hit(mx, my, bx, by, totalW, Theme.FIELD_H)) return false;
        float sc = host.scrollOf(this);
        host.openPopup(new DropdownPopup(s, bx, Math.round(by - sc) + Theme.FIELD_H + 2, totalW));
        return true;
    }
}
