package dev.umbra.ui.widget;

import dev.umbra.ui.Draw;
import dev.umbra.ui.Theme;

/** Всплывающая панель поверх окна (список, пипетка). Координаты — экранные, без прокрутки. */
public abstract class Popup extends Widget {
    private boolean closed;

    public boolean isClosed() { return closed; }
    public void close() { closed = true; }

    /** Удержать попап внутри прямоугольника окна. */
    public void clampInto(int bx, int by, int bw, int bh) {
        if (x + w > bx + bw - 4) x = bx + bw - 4 - w;
        if (x < bx + 4) x = bx + 4;
        if (y + h > by + bh - 4) y = y - h - Theme.FIELD_H - 6;
        if (y < by + 4) y = by + 4;
    }

    protected void frame(Draw d) {
        d.round(x - 1, y + 2, w + 2, h + 2, Theme.RADIUS_SM + 1, Theme.SHADOW);
        d.card(x, y, w, h, Theme.RADIUS_SM + 1, Theme.POPUP, Theme.WINDOW_EDGE);
    }

    /** @return true, если клик внутри (поглощён). Клик снаружи окно закроет попап само. */
    @Override
    public abstract boolean mouseClicked(double mx, double my, int button);
}
