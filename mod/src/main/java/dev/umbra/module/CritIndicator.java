package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.HudLayer;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Скобки у прицела, когда удар сейчас будет критом: ты падаешь, удар заряжен, не бежишь, не в воде.
 * Это те же условия, что ты видишь сам, — подсказка, а не автоматика: бить всё равно тебе.
 */
public final class CritIndicator extends Module implements HudLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFF2C46B));
    final BoolSetting onlyTarget = add(new BoolSetting("only_target", "Только при наведении на цель", true));

    private float shown;
    private long last;

    public CritIndicator() {
        super("crit_indicator", "Crit Indicator", "Подсказка у прицела, когда удар будет критом.", Category.VISUALS);
    }

    static boolean critReady(LocalPlayer p) {
        return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
                && !p.isSprinting() && !p.hasEffect(MobEffects.BLINDNESS) && p.getAttackStrengthScale(0.5f) > 0.9f;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean on = critReady(p) && (!onlyTarget.get() || mc.crosshairPickEntity instanceof LivingEntity);
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        shown = Anim.to(shown, on ? 1 : 0, dt);
        if (shown < 0.02f) return;

        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int r = 9 - Math.round(shown * 2);
        int c = Colors.mulAlpha(color.get(), shown);
        int l = 4;
        // четыре уголка вокруг прицела
        g.fill(cx - r, cy - r, cx - r + l, cy - r + 1, c); g.fill(cx - r, cy - r, cx - r + 1, cy - r + l, c);
        g.fill(cx + r - l + 1, cy - r, cx + r + 1, cy - r + 1, c); g.fill(cx + r, cy - r, cx + r + 1, cy - r + l, c);
        g.fill(cx - r, cy + r, cx - r + l, cy + r + 1, c); g.fill(cx - r, cy + r - l + 1, cx - r + 1, cy + r + 1, c);
        g.fill(cx + r - l + 1, cy + r, cx + r + 1, cy + r + 1, c); g.fill(cx + r, cy + r - l + 1, cx + r + 1, cy + r + 1, c);
    }
}
