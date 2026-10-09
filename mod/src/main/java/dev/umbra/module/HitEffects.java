package dev.umbra.module;

import dev.umbra.core.AttackListener;
import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Частицы при твоём ударе. Только у тебя на экране. */
public final class HitEffects extends Module implements AttackListener {
    private static final ParticleOptions[] TYPES = {
            ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.END_ROD,
            ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.HEART, ParticleTypes.CLOUD};

    final EnumSetting type = add(new EnumSetting("type", "Частицы", 1,
            "crit", "Криты", "magic", "Магия", "spark", "Искры", "soul", "Души", "heart", "Сердца", "cloud", "Облако"));
    final SliderSetting count = add(new SliderSetting("count", "Количество", 12, 2, 40, 1, ""));
    final SliderSetting speed = add(new SliderSetting("speed", "Разлёт", 0.25, 0.05, 1, 0.05, ""));

    private final RandomSource rnd = RandomSource.create();

    public HitEffects() {
        super("hit_effects", "Hit Effects", "Частицы вокруг цели при каждом твоём ударе.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ParticleOptions p = TYPES[type.get()];
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.6, z = target.getZ();
        double v = speed.get();
        int n = count.getI();
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(p, x + (rnd.nextDouble() - 0.5) * target.getBbWidth(), y + (rnd.nextDouble() - 0.5) * 0.6,
                    z + (rnd.nextDouble() - 0.5) * target.getBbWidth(),
                    (rnd.nextDouble() - 0.5) * v, rnd.nextDouble() * v * 0.6, (rnd.nextDouble() - 0.5) * v);
        }
    }
}
