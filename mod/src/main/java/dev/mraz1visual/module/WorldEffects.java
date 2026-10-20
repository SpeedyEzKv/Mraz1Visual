package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Атмосферные частицы вокруг тебя: снег, пепел, светлячки. Только у тебя. */
public final class WorldEffects extends Module {
    private static final ParticleOptions[] TYPES = {ParticleTypes.SNOWFLAKE, ParticleTypes.WHITE_ASH, ParticleTypes.END_ROD, ParticleTypes.SPORE_BLOSSOM_AIR};

    final EnumSetting type = add(new EnumSetting("type", "Эффект", 0, "snow", "Снег", "ash", "Пепел", "glow", "Огоньки", "spores", "Пыльца"));
    final SliderSetting density = add(new SliderSetting("density", "Плотность", 4, 1, 20, 1, ""));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 12, 4, 32, 1, " бл."));

    private final RandomSource rnd = RandomSource.create();

    public WorldEffects() {
        super("world_effects", "World Effects", "Снег, пепел или огоньки вокруг тебя.", Category.SKY);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ParticleOptions t = TYPES[type.get()];
        double r = radius.get();
        boolean falls = type.get() <= 1;
        for (int i = 0; i < density.getI(); i++) {
            double x = p.getX() + (rnd.nextDouble() * 2 - 1) * r;
            double z = p.getZ() + (rnd.nextDouble() * 2 - 1) * r;
            double y = p.getY() + (falls ? 6 + rnd.nextDouble() * 6 : rnd.nextDouble() * 6 - 1);
            double vy = falls ? -0.05 - rnd.nextDouble() * 0.05 : 0.005;
            mc.level.addParticle(t, x, y, z, (rnd.nextDouble() - 0.5) * 0.02, vy, (rnd.nextDouble() - 0.5) * 0.02);
        }
    }
}
