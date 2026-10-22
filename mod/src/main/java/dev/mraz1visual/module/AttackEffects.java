package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Вспышка в точке удара: яркое ядро, лучи и ударная волна. */
public final class AttackEffects extends Module implements AttackListener, WorldLayer {
    final EnumSetting effect = add(new EnumSetting("effect", "Эффект", 0, "flash", "Вспышка с лучами", "wave", "Ударная волна", "star", "Звезда", "all", "Всё вместе"));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 0.8, 0.3, 2, 0.05, " бл."));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 1, 0.3, 2, 0.05, "×"));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 0.35, 0.15, 1, 0.05, " с"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 0, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFB8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFFFFB86A));

    private static final class Hit {
        double x, y, z;
        long at;
        float[] rays;
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Random rnd = new Random();

    public AttackEffects() {
        super("attack_effects", "Attack Effects", "Вспышка, лучи и волна в точке удара.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Hit h = new Hit();
        double dx = mc.player.getX() - e.getX(), dz = mc.player.getZ() - e.getZ(), len = Math.max(1e-3, Math.hypot(dx, dz));
        h.x = e.getX() + dx / len * e.getBbWidth() * 0.5;
        h.y = Math.min(mc.player.getEyeY(), e.getY() + e.getBbHeight() * 0.85);
        h.z = e.getZ() + dz / len * e.getBbWidth() * 0.5;
        h.at = System.currentTimeMillis();
        h.rays = new float[8];
        for (int i = 0; i < h.rays.length; i++) h.rays[i] = 0.6f + rnd.nextFloat() * 0.6f;
        hits.add(h);
        if (hits.size() > 12) hits.remove(0);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        hits.removeIf(h -> now - h.at > life);
        String m = effect.getId();
        float br = brightness.getF();
        double r = radius.get();
        for (Hit h : hits) {
            float t = (now - h.at) / (float) life;
            float a = Math.min(1f, (1 - t) * br);
            float e = 1 - (1 - t) * (1 - t);
            int c1 = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), 0) | 0xFF000000;
            int c2 = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), 0.5f) | 0xFF000000;
            boolean all = m.equals("all");
            // ядро
            d.billboardDot(h.x, h.y, h.z, r * 0.5 * (1 - t * 0.5), Colors.mulAlpha(c1, a * 0.45f), 10);
            d.billboardDot(h.x, h.y, h.z, r * 0.15 * (1 - t), Colors.mulAlpha(0xFFFFFFFF, dev.mraz1visual.render.Fx.flash(a)), 8);
            if (m.equals("flash") || all) {
                for (int i = 0; i < h.rays.length; i++) {
                    double ang = i * Math.PI * 2 / h.rays.length + h.rays[i];
                    double len = r * h.rays[i] * (0.4 + e * 0.8);
                    ray(d, h, ang, len, Colors.mulAlpha(c2, a));
                }
            }
            if (m.equals("wave") || all) {
                d.billboardRing(h.x, h.y, h.z, r * (0.2 + e * 1.1), 0.06 * (1 - t) + 0.01, 40, Colors.mulAlpha(c1, a));
                d.billboardRing(h.x, h.y, h.z, r * (0.1 + e * 0.7), 0.03 * (1 - t) + 0.005, 32, Colors.mulAlpha(c2, a * 0.7f));
            }
            if (m.equals("star") || all) {
                d.billboardCorners(h.x, h.y, h.z, r * 0.35 * (0.5 + e), t * 3, 0.04 * (1 - t) + 0.01, Colors.mulAlpha(c2, a));
            }
        }
    }

    /** Луч в плоскости экрана: от центра наружу, сужается к концу. */
    private static void ray(WorldDraw d, Hit h, double ang, double len, int c) {
        d.billboardRay(h.x, h.y, h.z, ang, len * 0.15, len, 0.05, c);
    }
}
