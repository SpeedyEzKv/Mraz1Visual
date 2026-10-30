package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Частицы: больше искр при ударе (как множитель частиц в Lunar) и меньше всех остальных — для FPS.
 * Лишние частицы отбрасываются ещё до создания в ParticleEngine.add, поэтому не стоят ничего.
 */
public final class ParticleMultiplier extends Module implements AttackListener {
    private static ParticleMultiplier instance;
    final SliderSetting hit = add(new SliderSetting("hit", "Искры при ударе", 2, 1, 10, 1, "×"));
    final BoolSetting alwaysCrit = add(new BoolSetting("always_crit", "Искры крита при каждом ударе", false));
    final BoolSetting sharpness = add(new BoolSetting("sharpness", "Синие искры (зачарование)", true));
    final SliderSetting amount = add(new SliderSetting("amount", "Остальные частицы", 100, 0, 100, 5, " %")
            .hint("Меньше частиц от взрывов, дождя, блоков — больше FPS."));

    private long seed = 0x9E3779B97F4A7C15L;

    public ParticleMultiplier() {
        super("particle_multiplier", "Particle Multiplier", "Больше искр при ударе, меньше прочих частиц.", Category.COMBAT);
        instance = this;
    }

    @Override
    public void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        Minecraft mc = Minecraft.getInstance();
        int extra = hit.getI() - 1 + (alwaysCrit.get() ? 1 : 0);
        for (int i = 0; i < extra; i++) {
            mc.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
            if (sharpness.get()) mc.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
        }
    }

    /** Из миксина ParticleEngine.add: true — частицу не добавлять. */
    public static boolean drop() {
        ParticleMultiplier m = instance;
        if (m == null || !m.isEnabled()) return false;
        int keep = m.amount.getI();
        if (keep >= 100) return false;
        if (keep <= 0) return true;
        // xorshift без аллокаций: Math.random() дороже на тысячах частиц
        long s = m.seed;
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        m.seed = s;
        return (int) ((s >>> 33) % 100) >= keep;
    }
}
