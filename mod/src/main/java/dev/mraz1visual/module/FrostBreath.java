package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Пар изо рта в холоде: в снежных биомах и высоко в горах раз в несколько секунд — маленькое мягкое облачко,
 * которое плывёт вперёд и тает. На бегу дышишь чаще. Только у тебя на экране.
 */
public final class FrostBreath extends Module {
    final BoolSetting always = add(new BoolSetting("always", "Всегда, не только в холоде", false));
    final BoolSetting firstPerson = add(new BoolSetting("first_person", "Видно от 1-го лица", true));
    final SliderSetting density = add(new SliderSetting("density", "Плотность", 100, 30, 200, 10, " %"));

    private int tick, puff;
    private int nextBreath = 40;

    public FrostBreath() {
        super("frost_breath", "Frost Breath", "Лёгкий пар изо рта в холодных биомах и высоко в горах.", Category.VISUALS);
    }

    private boolean cold(Minecraft mc, LocalPlayer p) {
        if (always.get()) return true;
        BlockPos pos = p.blockPosition();
        try {
            if (mc.level.getBiome(pos).value().coldEnoughToSnow(pos, mc.level.getSeaLevel())) return true;
        } catch (Throwable ignored) {
        }
        return p.getY() > 190;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isUnderWater()) return;
        if (!firstPerson.get() && mc.options.getCameraType().isFirstPerson()) return;
        if (!cold(mc, p)) return;
        tick++;
        if (puff > 0) {
            puff--;
            Vec3 look = p.getLookAngle();
            Vec3 eye = p.getEyePosition();
            int n = Math.max(1, Math.round(2 * density.getF() / 100f * Fx.quality()));
            for (int i = 0; i < n; i++) {
                double v = 0.5 + Math.random() * 0.3;
                FxParticles.spawn(eye.x + look.x * 0.32, eye.y - 0.12 + look.y * 0.32, eye.z + look.z * 0.32,
                        look.x * v + (Math.random() - 0.5) * 0.12, look.y * v + 0.08, look.z * v + (Math.random() - 0.5) * 0.12,
                        1.1f + (float) Math.random() * 0.5f, 0.07f + (float) Math.random() * 0.05f, 0x34F4F8FF, -0.08f, false,
                        FxParticles.DOT, false, 0.25f, 0.45f);
            }
        } else if (tick >= nextBreath) {
            tick = 0;
            puff = 6;
            nextBreath = (p.isSprinting() ? 28 : 52) + (int) (Math.random() * 16);
        }
    }
}
