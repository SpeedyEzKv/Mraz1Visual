package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Огоньки из-под ног при прыжке. */
public final class JumpParticles extends Module {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "fountain", "Фонтан", "ring", "Кольцо", "dust", "Пыль"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFB8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFF7FD8FF));
    final SliderSetting count = add(new SliderSetting("count", "Количество", 16, 4, 40, 1, ""));

    private boolean wasOnGround = true;

    public JumpParticles() {
        super("jump_particles", "Jump Particles", "Огоньки из-под ног при прыжке.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean on = p.onGround();
        if (wasOnGround && !on && p.getDeltaMovement().y > 0.1) {
            int n = count.getI();
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + Math.random() * 0.3;
                int c = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), i / (float) n) | 0xFF000000;
                switch (style.getId()) {
                    case "ring" -> FxParticles.spawn(p.getX() + Math.cos(a) * 0.3, p.getY() + 0.05, p.getZ() + Math.sin(a) * 0.3,
                            Math.cos(a) * 2.2, 0.2, Math.sin(a) * 2.2, 0.6f, 0.07f, c, 0.5f, true);
                    case "dust" -> FxParticles.spawn(p.getX() + (Math.random() - 0.5) * 0.6, p.getY() + 0.05, p.getZ() + (Math.random() - 0.5) * 0.6,
                            (Math.random() - 0.5) * 0.6, 0.3 + Math.random() * 0.4, (Math.random() - 0.5) * 0.6, 1.2f, 0.12f, c & 0x90FFFFFF, -0.2f, false);
                    default -> FxParticles.spawn(p.getX(), p.getY() + 0.1, p.getZ(),
                            Math.cos(a) * (0.8 + Math.random()), 2.5 + Math.random() * 1.5, Math.sin(a) * (0.8 + Math.random()), 0.9f, 0.07f, c, 7f, true);
                }
            }
        }
        wasOnGround = on;
    }
}
