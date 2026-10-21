package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Кинорежим для кадров и скриншотов: по клавише плавно выезжают киношные полосы, весь HUD прячется,
 * камера становится плавной (ванильная «кинокамера»), по краям — лёгкая виньетка. Повторное нажатие — обратно.
 */
public final class Cinematic extends Module {
    final KeySetting key = add(new KeySetting("key", "Клавиша", GLFW.GLFW_KEY_F8));
    final SliderSetting bars = add(new SliderSetting("bars", "Полосы", 11, 4, 20, 1, " %"));
    final BoolSetting smooth = add(new BoolSetting("smooth", "Плавная камера", true));
    final BoolSetting vignette = add(new BoolSetting("vignette", "Виньетка", true));

    private static Cinematic instance;
    private boolean on, keyWas, smoothWas;
    private float t;
    private long last;

    public Cinematic() {
        super("cinematic", "Cinematic", "Кинорежим по клавише: полосы, без HUD, плавная камера — для красивых кадров.", Category.VISUALS);
        instance = this;
    }

    @Override
    protected void onDisable() {
        if (on) toggle(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = mc.screen == null && Keys.isDown(key.get());
        if (down && !keyWas) toggle(mc);
        keyWas = down;
    }

    /** Для dev-стенда. */
    public static void harnessToggle() {
        if (instance != null) instance.toggle(Minecraft.getInstance());
    }

    private void toggle(Minecraft mc) {
        on = !on;
        if (smooth.get()) {
            if (on) {
                smoothWas = mc.options.smoothCamera;
                mc.options.smoothCamera = true;
            } else {
                mc.options.smoothCamera = smoothWas;
            }
        }
    }

    /** Из миксина Gui.render: в кинорежиме вместо HUD — полосы. @return true — ванильный HUD не рисовать. */
    public static boolean render(GuiGraphics g) {
        Cinematic c = instance;
        if (c == null || !c.isEnabled()) return false;
        long now = System.nanoTime();
        float dt = c.last == 0 ? 0 : Math.min(0.05f, (now - c.last) / 1e9f);
        c.last = now;
        c.t = Ease.approach(c.t, c.on ? 1 : 0, 6, dt);
        if (c.t < 0.005f) return false;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float e = Ease.inOutSine(c.t);
        int bh = Math.round(sh * c.bars.getF() / 100f * e);
        if (bh > 0) {
            g.fill(0, 0, sw, bh, 0xFF000000);
            g.fill(0, sh - bh, sw, sh, 0xFF000000);
            // мягкий край полос — без резкой границы
            g.fillGradient(0, bh, sw, bh + 6, 0x66000000, 0x00000000);
            g.fillGradient(0, sh - bh - 6, sw, sh - bh, 0x00000000, 0x66000000);
        }
        if (c.vignette.get()) {
            int a = Math.round(0x50 * e);
            int vw = Math.round(sw * 0.18f);
            g.pose().pushMatrix();
            g.pose().rotate((float) (-Math.PI / 2));
            g.pose().translate(-sh, 0);
            g.fillGradient(0, 0, sh, vw, a << 24, 0);
            g.fillGradient(0, sw - vw, sh, sw, 0, a << 24);
            g.pose().popMatrix();
        }
        return c.t > 0.5f; // HUD прячется, когда полосы выехали наполовину
    }
}
