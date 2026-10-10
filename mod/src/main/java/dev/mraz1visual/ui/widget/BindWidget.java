package dev.mraz1visual.ui.widget;

import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;

/**
 * Кнопка-бинд. Клик — ждёт клавишу. Esc / Backspace / Delete — снять бинд.
 * Используется и как строка настройки, и отдельно в шапке модуля.
 */
public final class BindWidget extends SettingWidget {
    private static final String WAIT = "...";
    private final KeySetting s;
    private final boolean showLabel;
    private boolean listening;
    private float btnHover;

    public BindWidget(KeySetting s, Host host, boolean showLabel) {
        super(s, host);
        this.s = s;
        this.showLabel = showLabel;
    }

    public int buttonWidth(Draw d) {
        return Math.max(28, d.width(listening ? WAIT : s.display()) + 12);
    }

    @Override
    public void render(Draw d, double mx, double my, float dt) {
        if (showLabel) super.render(d, mx, my, dt);
        else renderControl(d, mx, my, dt);
    }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        int bw = buttonWidth(d);
        int bx = right() - bw, by = y + (h - Theme.FIELD_H) / 2;
        btnHover = Anim.to(btnHover, Widget.hit(mx, my, bx, by, bw, Theme.FIELD_H) ? 1 : 0, dt);
        d.round(bx, by, bw, Theme.FIELD_H, Theme.RADIUS_SM, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, btnHover));
        String label = listening ? WAIT : s.display();
        int color = listening ? Theme.accent() : s.isBound() ? Theme.TEXT : Theme.TEXT_MUTED;
        d.textCenter(label, bx + bw / 2, Draw.textY(by, Theme.FIELD_H), color);
    }

    @Override
    protected boolean clickControl(double mx, double my) {
        listening = true;
        host.startListening(this);
        return true;
    }

    public void onKey(int key) {
        listening = false;
        s.set(Keys.isUnbindKey(key) ? KeySetting.NONE : key);
    }

    public void cancel() {
        listening = false;
    }
}
