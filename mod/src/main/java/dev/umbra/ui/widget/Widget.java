package dev.umbra.ui.widget;

import dev.umbra.ui.Draw;

/** Базовый прямоугольник интерфейса. Координаты — в единицах окна. */
public abstract class Widget {
    public int x, y, w, h;

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean hit(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public abstract void render(Draw d, double mx, double my, float dt);

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public void mouseDragged(double mx, double my) {}
    public void mouseReleased(double mx, double my) {}
}
