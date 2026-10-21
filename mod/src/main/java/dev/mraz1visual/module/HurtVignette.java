package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * Мягкая цветная виньетка по краям экрана, когда тебя бьют: сила — от урона, плавное нарастание и затухание.
 * При малом здоровье — едва заметное медленное «дыхание» краёв. Вместо резкой красной вспышки.
 */
public final class HurtVignette extends Module implements HudLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFFF4D6D));
    final SliderSetting strength = add(new SliderSetting("strength", "Сила", 60, 10, 100, 5, " %"));
    final SliderSetting width = add(new SliderSetting("width", "Ширина края", 22, 8, 40, 1, " %"));
    final BoolSetting lowHp = add(new BoolSetting("low_hp", "Дыхание при малом здоровье", true));

    private float level, target;
    private float lastHealth = -1;
    private long last;

    public HurtVignette() {
        super("hurt_vignette", "Hurt Vignette", "Мягкая цветная виньетка по краям экрана при уроне.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        float h = p.getHealth();
        if (lastHealth >= 0 && h < lastHealth) target = Math.min(1f, target + 0.35f + (lastHealth - h) / 8f);
        lastHealth = h;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (p == null) return;
        level = Ease.approach(level, target, 14, dt);
        target = Ease.approach(target, 0, 1.6f, dt);
        float breath = 0;
        if (lowHp.get() && p.getHealth() <= 6 && !p.isDeadOrDying()) {
            breath = (0.18f + 0.12f * (float) Math.sin(Fx.time() * 2.2)) * (1 - p.getHealth() / 6f);
        }
        float a = Math.max(level * 0.75f, breath) * strength.getF() / 100f * Fx.brightness();
        if (Fx.noFlash()) a = Math.min(a, 0.35f);
        if (a < 0.01f) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int c = Colors.mulAlpha(color.get(), a);
        int clear = c & 0x00FFFFFF;
        int bw = Math.round(Math.min(sw, sh) * width.getF() / 100f);
        // четыре мягких края; в углах они складываются — углы чуть плотнее, как у настоящей виньетки
        g.fillGradient(0, 0, sw, bw, c, clear);
        g.fillGradient(0, sh - bw, sw, sh, clear, c);
        g.pose().pushMatrix();
        g.pose().rotate((float) (-Math.PI / 2));
        g.pose().translate(-sh, 0);
        g.fillGradient(0, 0, sh, bw, c, clear);
        g.fillGradient(0, sw - bw, sh, sw, clear, c);
        g.pose().popMatrix();
    }
}
