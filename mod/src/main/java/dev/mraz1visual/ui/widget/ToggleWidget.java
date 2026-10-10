package dev.mraz1visual.ui.widget;

import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;

public final class ToggleWidget extends SettingWidget {
    private final BoolSetting s;
    private float t;

    public ToggleWidget(BoolSetting s, Host host) {
        super(s, host);
        this.s = s;
        this.t = s.get() ? 1 : 0;
    }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        t = Anim.to(t, s.get() ? 1 : 0, dt);
        d.toggle(right() - Theme.SWITCH_W, y + (h - Theme.SWITCH_H) / 2, t);
    }

    /** Клик в любом месте строки переключает — быстрее, чем целиться в тумблер. */
    @Override
    protected boolean clickControl(double mx, double my) {
        s.toggle();
        return true;
    }
}
