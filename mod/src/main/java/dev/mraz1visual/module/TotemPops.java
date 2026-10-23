package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Счётчик снесённых тотемов у игроков рядом: сообщение в чат (только тебе) и
 * золотое кольцо на месте срабатывания. Это та же анимация тотема, что видят все.
 */
public final class TotemPops extends Module implements WorldLayer {
    private static TotemPops instance;
    final BoolSetting chat = add(new BoolSetting("chat", "Сообщение в чат", true));
    final BoolSetting ring = add(new BoolSetting("ring", "Кольцо на месте тотема", true));
    final ColorSetting color = add(new ColorSetting("color", "Цвет кольца", 0xFFF2D25A));

    private final Map<String, Integer> pops = new HashMap<>();
    private final List<double[]> rings = new ArrayList<>(); // x, y, z, start
    private Object level;

    public TotemPops() {
        super("totem_pops", "Totem Pops", "Сколько тотемов снёс каждый игрок рядом.", Category.VISUALS);
        instance = this;
    }

    /** Из миксина LivingEntity.handleEntityEvent(35). */
    public static void onPop(Entity e) {
        TotemPops t = instance;
        if (t == null || !t.isEnabled() || !(e instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        String name = p.getName().getString();
        int n = t.pops.merge(name, 1, Integer::sum);
        if (t.chat.get() && mc.player != null) {
            String who = p == mc.player ? "§bТы" : "§f" + name;
            dev.mraz1visual.compat.Mc.message(mc.player, Component.literal("§7Mraz1Visual: " + who + "§7 снёс тотем §e#" + n), false);
        }
        if (t.ring.get()) t.rings.add(new double[]{p.getX(), p.getY(), p.getZ(), System.currentTimeMillis(), p.getBbHeight()});
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            pops.clear();
        }
        if (mc.level == null) return;
        for (Player p : mc.level.players()) if (p.isDeadOrDying()) pops.remove(p.getName().getString());
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.currentTimeMillis();
        rings.removeIf(r -> now - r[3] > 1200);
        for (double[] r : rings) {
            float t = (float) ((now - r[3]) / 1200.0);
            float a = 1f - t;
            double rad = 0.4 + t * 1.6;
            int c = color.get();
            for (int k = 0; k < 3; k++) {
                double y = r[1] + r[4] * (0.2 + 0.3 * k) + t * 0.5;
                d.ring(r[0], y, r[2], rad - 0.05, rad, 40, Colors.mulAlpha(c, a * 0.15f), Colors.mulAlpha(c, a));
            }
        }
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    @SuppressWarnings("unused")
    private static boolean living(Entity e) { return e instanceof LivingEntity; }
}
