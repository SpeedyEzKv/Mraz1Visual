package dev.mraz1visual.render;

import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Место элемента HUD на экране — для редактора HUD (перетаскивание, масштаб, привязка к краям).
 * x и y — доля свободного места: 0 — прижат к левому/верхнему краю, 1 — к правому/нижнему.
 * Так элемент не уезжает за экран при смене размера окна и остаётся привязан к своему краю.
 *
 * Модуль создаёт его через {@link Module#hudPos}, а в кадре зовёт {@link #begin} … {@link #end}.
 */
public final class HudPos {
    private static final List<HudPos> ALL = new ArrayList<>();
    private static final List<HudPos> ALL_VIEW = Collections.unmodifiableList(ALL);

    public final Module module;
    public final SliderSetting x, y, scale;
    /** Где элемент нарисован в прошлом кадре (координаты GUI, с учётом масштаба). */
    public int lastX, lastY, lastW, lastH;
    public long lastFrame;
    /** Своё место включено (у модулей со старым выбором угла — вариант «Свободно»). */
    private java.util.function.BooleanSupplier active = () -> true;
    private Runnable activate = () -> {};

    public HudPos(Module module, double fx, double fy) {
        this.module = module;
        this.x = new SliderSetting("hud_x", "Положение X", fx, 0, 1, 0.001, "").group("Положение на экране");
        this.y = new SliderSetting("hud_y", "Положение Y", fy, 0, 1, 0.001, "").group("Положение на экране");
        this.scale = new SliderSetting("hud_scale", "Масштаб", 1, 0.5, 2.5, 0.05, "×").group("Положение на экране");
        x.format(v -> Math.round(v * 100) + " %");
        y.format(v -> Math.round(v * 100) + " %");
        ALL.add(this);
    }

    /** Для модулей с выбором угла: своё место действует, только когда выбран вариант «Свободно». */
    public HudPos activeWhen(java.util.function.BooleanSupplier active, Runnable activate) {
        this.active = active;
        this.activate = activate;
        return this;
    }

    public boolean active() { return active.getAsBoolean(); }

    /** Редактор сдвинул элемент — включить свободное место. */
    public void activate() { activate.run(); }

    public static List<HudPos> all() { return ALL_VIEW; }

    /**
     * Начать рисование элемента размером w×h (в единицах до масштаба). После вызова рисуй от (0, 0).
     * Обязательно закрыть {@link #end}.
     */
    public void begin(GuiGraphics g, int w, int h) {
        float s = scale.getF();
        int sw = Math.round(w * s), sh = Math.round(h * s);
        int px = (int) Math.round(x.get() * Math.max(0, g.guiWidth() - sw));
        int py = (int) Math.round(y.get() * Math.max(0, g.guiHeight() - sh));
        lastX = px; lastY = py; lastW = sw; lastH = sh;
        lastFrame = frame;
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        if (s != 1f) g.pose().scale(s, s);
    }

    public void end(GuiGraphics g) {
        g.pose().popMatrix();
    }

    /** Запомнить место без смены матрицы — для модулей, которые рисуют в своих координатах. */
    public void track(int x, int y, int w, int h) {
        lastX = x; lastY = y; lastW = w; lastH = h;
        lastFrame = frame;
    }

    /** Нарисован ли элемент в последних кадрах (выключенные и пустые редактор не показывает). */
    public boolean visible() {
        return module.isEnabled() && frame - lastFrame <= 2;
    }

    /** Поставить левый верхний угол в точку экрана (из редактора). */
    public void moveTo(int px, int py, int screenW, int screenH) {
        int fw = Math.max(1, screenW - lastW), fh = Math.max(1, screenH - lastH);
        x.set(Math.max(0, Math.min(1, px / (double) fw)));
        y.set(Math.max(0, Math.min(1, py / (double) fh)));
    }

    private static long frame;

    /** Счётчик кадров HUD — зовёт HudDraw раз в кадр. */
    public static void nextFrame() { frame++; }
}
