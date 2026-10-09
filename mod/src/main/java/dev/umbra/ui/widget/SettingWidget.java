package dev.umbra.ui.widget;

import dev.umbra.setting.Setting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Theme;

/** Строка настройки: подпись слева, контрол справа. ПКМ по строке — сброс к умолчанию. */
public abstract class SettingWidget extends Widget {
    protected final Setting<?> setting;
    protected final Host host;
    protected float hover;

    public Setting<?> setting() { return setting; }

    protected SettingWidget(Setting<?> setting, Host host) {
        this.setting = setting;
        this.host = host;
    }

    @Override
    public void render(Draw d, double mx, double my, float dt) {
        hover = Anim.to(hover, hit(mx, my) ? 1 : 0, dt);
        d.text(setting.name, x, Draw.textY(y, h), Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover));
        renderControl(d, mx, my, dt);
    }

    protected abstract void renderControl(Draw d, double mx, double my, float dt);

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!hit(mx, my)) return false;
        if (button == 1) {
            setting.reset();
            return true;
        }
        return button == 0 && clickControl(mx, my);
    }

    protected abstract boolean clickControl(double mx, double my);

    /** Правый край для контрола. */
    protected int right() { return x + w; }
}
