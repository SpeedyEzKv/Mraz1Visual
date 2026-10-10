package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Всплывающие цифры урона и лечения над существами, которых ты видишь. */
public final class DamageNumbers extends Module implements WorldLayer {
    final BoolSetting heal = add(new BoolSetting("heal", "Показывать лечение", true));
    final BoolSetting hearts = add(new BoolSetting("hearts", "В сердечках (÷2)", true));
    final SliderSetting scale = add(new SliderSetting("scale", "Размер", 1.2, 0.6, 2.5, 0.05, "×"));

    private final Map<Integer, Float> hp = new HashMap<>();
    private final List<double[]> pops = new ArrayList<>(); // x y z start amount
    private final Random rnd = new Random();

    public DamageNumbers() {
        super("damage_numbers", "Damage Numbers", "Цифры урона и лечения над целями.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            hp.clear();
            return;
        }
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e.distanceToSqr(mc.player) > 32 * 32) continue;
            float h = le.getHealth() + le.getAbsorptionAmount();
            Float was = hp.get(e.getId());
            seen.put(e.getId(), h);
            if (was == null || Math.abs(was - h) < 0.05f || !mc.player.hasLineOfSight(e)) continue;
            float diff = h - was;
            if (diff > 0 && !heal.get()) continue;
            pops.add(new double[]{e.getX() + (rnd.nextDouble() - 0.5) * 0.6, e.getY() + e.getBbHeight() + 0.2, e.getZ() + (rnd.nextDouble() - 0.5) * 0.6,
                    System.currentTimeMillis(), diff});
        }
        hp.clear();
        hp.putAll(seen);
    }

    @Override
    protected void onDisable() {
        hp.clear();
        pops.clear();
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.currentTimeMillis();
        pops.removeIf(p -> now - p[3] > 1100);
        for (double[] p : pops) {
            float t = (float) ((now - p[3]) / 1100.0);
            float v = (float) Math.abs(p[4]) / (hearts.get() ? 2f : 1f);
            String s = (p[4] < 0 ? "-" : "+") + (v >= 10 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v));
            ChatFormatting c = p[4] < 0 ? (v >= 4 ? ChatFormatting.RED : ChatFormatting.GOLD) : ChatFormatting.GREEN;
            int a = Math.max(4, Math.round(255 * (t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f)));
            float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.6f : 1.2f - Math.min(0.2f, (t - 0.15f));
            d.label(Component.literal(s).withStyle(c), p[0], p[1] + t * 0.8, p[2], scale.getF() * pop, a << 24 | 0xFFFFFF, 0);
        }
    }
}
