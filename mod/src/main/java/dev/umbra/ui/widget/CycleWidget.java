package dev.umbra.ui.widget;

import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;

/** Выбор стрелками: ‹ значение ›. Левая половина — назад, правая — вперёд. */
public final class CycleWidget extends SettingWidget {
    private final EnumSetting s;
    private final int boxW;
    private float l, r;

    public CycleWidget(EnumSetting s, Host host) {
        super(s, host);
        this.s = s;
        int max = 0;
        for (String lab : s.labels) max = Math.max(max, Fonts.width(lab, Fonts.Kind.BOLD));
        this.boxW = max + 34;
    }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        int bx = right() - boxW, ty = Draw.textY(y, h);
        boolean inBox = Widget.hit(mx, my, bx, y, boxW, h);
        l = Anim.to(l, inBox && mx < bx + boxW / 2.0 ? 1 : 0, dt);
        r = Anim.to(r, inBox && mx >= bx + boxW / 2.0 ? 1 : 0, dt);
        d.text("‹", bx, ty, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, l));
        d.textRight("›", bx + boxW, ty, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, r));
        String lab = s.label();
        d.text(lab, Fonts.Kind.BOLD, bx + (boxW - Fonts.width(lab, Fonts.Kind.BOLD)) / 2, ty, Theme.TEXT);
    }

    @Override
    protected boolean clickControl(double mx, double my) {
        int bx = right() - boxW;
        int n = s.size();
        if (mx < bx + boxW / 2.0) s.set((s.get() - 1 + n) % n);
        else s.set((s.get() + 1) % n);
        return true;
    }
}
