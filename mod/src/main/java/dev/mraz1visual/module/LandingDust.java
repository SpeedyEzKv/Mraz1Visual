package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Мягкая пыль цвета блока под ногами: облачко при приземлении (сильнее — с высоты) и редкие клубы на бегу.
 * Вместо ванильных квадратных крошек — круглые полупрозрачные клубы, которые медленно расползаются и тают.
 */
public final class LandingDust extends Module {
    final BoolSetting sprint = add(new BoolSetting("sprint", "Клубы на бегу", true));
    final SliderSetting amount = add(new SliderSetting("amount", "Количество", 100, 30, 200, 10, " %"));

    private boolean wasOnGround = true;
    private double fallFrom;
    private int stepTick;

    public LandingDust() {
        super("landing_dust", "Landing Dust", "Мягкая пыль цвета блока при приземлении и на бегу.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean on = p.onGround();
        if (!on && wasOnGround) fallFrom = p.getY();
        if (!on) fallFrom = Math.max(fallFrom, p.getY());
        if (on && !wasOnGround) {
            double h = fallFrom - p.getY();
            if (h > 0.6) puff(mc, p, (int) Math.min(26, 6 + h * 4), 0.6 + Math.min(1.6, h * 0.25));
        }
        if (on && sprint.get() && p.isSprinting() && ++stepTick % 6 == 0) puff(mc, p, 2, 0.25);
        wasOnGround = on;
    }

    private void puff(Minecraft mc, LocalPlayer p, int n, double power) {
        BlockPos below = p.blockPosition().below();
        BlockState st = mc.level.getBlockState(below);
        if (st.isAir()) return;
        int base = 0xFF000000 | st.getMapColor(mc.level, below).col;
        int c = Colors.withAlpha(Colors.lerp(base, 0xFFEDEDED, 0.35f), 0x70);
        int count = Math.max(1, Math.round(n * amount.getF() / 100f * Fx.quality()));
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2, v = power * (0.6 + Math.random() * 0.6);
            FxParticles.spawn(p.getX() + Math.cos(a) * 0.2, p.getY() + 0.05, p.getZ() + Math.sin(a) * 0.2,
                    Math.cos(a) * v, 0.15 + Math.random() * 0.25, Math.sin(a) * v, 0.7f + (float) Math.random() * 0.6f,
                    0.1f + (float) Math.random() * 0.08f, c, 0.4f, false, FxParticles.DOT, false, 0.08f, 0.3f);
        }
    }
}
