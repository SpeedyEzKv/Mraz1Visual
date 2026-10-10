package dev.mraz1visual.ui.widget;

import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;

public final class SliderWidget extends SettingWidget {
    private final SliderSetting s;
    private boolean dragging;
    private float shown;

    public SliderWidget(SliderSetting s, Host host) {
        super(s, host);
        this.s = s;
        this.shown = (float) s.fraction();
    }

    private int trackX() { return right() - Theme.SLIDER_W; }

    @Override
    protected void renderControl(Draw d, double mx, double my, float dt) {
        shown = dragging ? (float) s.fraction() : Anim.to(shown, (float) s.fraction(), dt);
        int tx = trackX();
        int cy = y + h / 2;
        int fillW = Math.round(Theme.SLIDER_W * shown);
        d.round(tx, cy - 1, Theme.SLIDER_W, 2, 1, Theme.TRACK);
        d.round(tx, cy - 1, fillW, 2, 1, Theme.accent());
        int knob = dragging || hover > 0.5f ? 8 : 7;
        d.circle(tx + fillW, cy, knob, Theme.KNOB_ON);
        d.textRight(s.display(), tx - Theme.GAP, Draw.textY(y, h), Theme.TEXT);
    }

    @Override
    protected boolean clickControl(double mx, double my) {
        int tx = trackX();
        if (mx < tx - 6 || mx > tx + Theme.SLIDER_W + 6) return false;
        dragging = true;
        apply(mx);
        return true;
    }

    @Override
    public void mouseDragged(double mx, double my) {
        if (dragging) apply(mx);
    }

    @Override
    public void mouseReleased(double mx, double my) {
        dragging = false;
    }

    private void apply(double mx) {
        s.setFraction((mx - trackX()) / Theme.SLIDER_W);
    }
}
