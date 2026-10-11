package dev.mraz1visual.ui.widget;

import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;

/** Кнопка справа в строке: «Выбрать ›». */
public final class ActionWidget extends SettingWidget {
    private final ActionSetting s;
    private float press;

    public ActionWidget(ActionSetting s, Host host) {
        super(s, host);
        this.s = s;
    }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        int bw = Fonts.width(s.button, Fonts.Kind.BOLD) + 14, bh = h - 6;
        int bx = right() - bw, by = y + 3;
        press = Anim.to(press, Widget.hit(mx, my, bx, by, bw, bh) ? 1 : 0, dt);
        d.round(bx, by, bw, bh, 3, Colors.lerp(Theme.accentSoft(), Theme.accent(), press * 0.6f));
        d.text(s.button, Fonts.Kind.BOLD, bx + 7, Draw.textY(by, bh), Theme.TEXT);
    }

    @Override
    protected boolean clickControl(double mx, double my) {
        s.run();
        return true;
    }
}
