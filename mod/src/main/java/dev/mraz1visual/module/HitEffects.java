package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Частицы при твоём ударе. Только у тебя на экране. */
public final class HitEffects extends Module implements AttackListener, dev.mraz1visual.core.WorldLayer {
    private static final ParticleOptions[] TYPES = {
            ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.END_ROD,
            ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.HEART, ParticleTypes.CLOUD};

    final EnumSetting type = add(new EnumSetting("type", "Частицы", 9,
            "crit", "Криты", "magic", "Магия", "spark", "Искры", "soul", "Души", "heart", "Сердца", "cloud", "Облако",
            "glow", "Огоньки Mraz1Visual", "burst", "Взрыв искр", "orbit", "Звёздная пыль", "wave", "Волна и искры"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет волны", 0, dev.mraz1visual.render.Palette.OPTIONS));
    final dev.mraz1visual.setting.EnumSetting colorMode = add(new dev.mraz1visual.setting.EnumSetting("color_mode", "Цвет огоньков", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final dev.mraz1visual.setting.ColorSetting color = add(new dev.mraz1visual.setting.ColorSetting("color", "Цвет 1", 0xFFB8B4F2));
    final dev.mraz1visual.setting.ColorSetting color2 = add(new dev.mraz1visual.setting.ColorSetting("color2", "Цвет 2", 0xFF7FD8FF));
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
        if (type.getId().equals("wave")) {
            wave(target);
            return;
        }
        if (type.get() >= TYPES.length) {
            custom(target);
            return;
        }
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

    // ── волна: мягкое кольцо расходится от точки удара (ease-out), несколько ярких искр быстро гаснут ──

    private static final int WAVES = 8;
    private final double[] wx = new double[WAVES], wy = new double[WAVES], wz = new double[WAVES];
    private final long[] wt = new long[WAVES];
    private int wHead;

    private void wave(Entity t) {
        wHead = (wHead + 1) % WAVES;
        wx[wHead] = t.getX();
        wy[wHead] = t.getY() + t.getBbHeight() * 0.55;
        wz[wHead] = t.getZ();
        wt[wHead] = System.nanoTime();
        int n = Math.min(10, Math.max(4, count.getI() / 2));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, b = (rnd.nextDouble() - 0.35) * Math.PI * 0.8;
            double v = 2.2 + rnd.nextDouble() * 1.6;
            int c = dev.mraz1visual.render.Palette.color(preset.getId(), 0.1f + rnd.nextFloat() * 0.4f, i, color.get(), color2.get());
            dev.mraz1visual.render.FxParticles.spawn(wx[wHead], wy[wHead], wz[wHead], Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v + 0.6, Math.sin(a) * Math.cos(b) * v,
                    0.28f + rnd.nextFloat() * 0.18f, 0.035f, c, 3f, true);
        }
    }

    @Override
    public void renderWorld(dev.mraz1visual.render.WorldDraw d, float pt) {
        long now = System.nanoTime();
        d.glowBegin();
        for (int i = 0; i < WAVES; i++) {
            if (wt[i] == 0) continue;
            float t = (now - wt[i]) / 4.2e8f;
            if (t >= 1f) { wt[i] = 0; continue; }
            float e = 1f - (float) Math.pow(1f - t, 3);           // ease-out
            float a = (1f - t) * (1f - t);
            double r = 0.25 + e * 0.95;
            int c = dev.mraz1visual.render.Palette.color(preset.getId(), 0.2f + t * 0.6f, 0, color.get(), color2.get());
            d.billboardRing(wx[i], wy[i], wz[i], r, 0.05 + 0.12 * (1 - e), 48, Colors.mulAlpha(c, 0.9f * a));
            d.billboardRing(wx[i], wy[i], wz[i], r, 0.22 * (1 - e) + 0.04, 48, Colors.mulAlpha(c, 0.22f * a));
            if (t < 0.25f) d.billboardDot(wx[i], wy[i], wz[i], 0.35 * (1 - t * 4), Colors.mulAlpha(0xFFFFFFFF, 0.5f * (1 - t * 4)), 14);
        }
        d.glowEnd();
    }

    /** Свои частицы: светящиеся огоньки с белой серединкой. */
    private void custom(Entity t) {
        double x = t.getX(), y = t.getY() + t.getBbHeight() * 0.6, z = t.getZ();
        double v = speed.get() * 6;
        int n = count.getI();
        String id = type.getId();
        for (int i = 0; i < n; i++) {
            int c = dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), color.get(), color2.get(), i / (float) n) | 0xFF000000;
            double a = rnd.nextDouble() * Math.PI * 2, b = (rnd.nextDouble() - 0.3) * Math.PI;
            double dx = Math.cos(a) * Math.cos(b), dy = Math.sin(b), dz = Math.sin(a) * Math.cos(b);
            switch (id) {
                case "burst" -> dev.mraz1visual.render.FxParticles.spawn(x, y, z, dx * v * 1.6, dy * v * 1.6 + 1, dz * v * 1.6,
                        0.6f + rnd.nextFloat() * 0.3f, 0.06f, c, 6f, true);
                case "orbit" -> dev.mraz1visual.render.FxParticles.spawn(x + dx * t.getBbWidth() * 0.6, y + dy * 0.6, z + dz * t.getBbWidth() * 0.6,
                        dx * 0.3, 0.6 + rnd.nextDouble() * 0.6, dz * 0.3, 1.3f + rnd.nextFloat() * 0.5f, 0.05f, c, -0.3f, true);
                default -> dev.mraz1visual.render.FxParticles.spawn(x + (rnd.nextDouble() - 0.5) * t.getBbWidth(), y + (rnd.nextDouble() - 0.5) * 0.6,
                        z + (rnd.nextDouble() - 0.5) * t.getBbWidth(), dx * v, dy * v + 0.5, dz * v, 0.9f + rnd.nextFloat() * 0.4f, 0.1f, c, 2.5f, true);
            }
        }
    }
}
