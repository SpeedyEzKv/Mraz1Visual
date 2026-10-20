package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * «Световые шаги» — своя идея: на каждом шаге на земле остаётся мягкий светящийся след стопы,
 * он вспыхивает и плавно тает. Левая и правая нога чередуются; на бегу шаги длиннее. Только твои шаги.
 */
public final class LightSteps extends Module implements WorldLayer {
    private static final int MAX = 32;

    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Центр (свой)", 0xFFEAF6FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Край (свой)", 0xFF7FD8FF));
    final SliderSetting life = add(new SliderSetting("life", "Сколько держится", 1.6, 0.5, 4, 0.1, " с"));
    final SliderSetting intensity = add(new SliderSetting("intensity", "Яркость", 70, 10, 100, 5, " %"));

    private final double[] sx = new double[MAX], sy = new double[MAX], sz = new double[MAX];
    private final float[] syaw = new float[MAX];
    private final long[] st = new long[MAX];
    private int head;
    private boolean left, has;
    private double walked, lastX, lastZ;

    public LightSteps() {
        super("light_steps", "Light Steps", "Светящиеся следы шагов, которые плавно тают.", Category.COSMETICS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!has) {
            lastX = p.getX();
            lastZ = p.getZ();
            has = true;
        }
        double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
        lastX = p.getX();
        lastZ = p.getZ();
        if (!p.onGround() || p.isInWater()) return;
        walked += Math.sqrt(dx * dx + dz * dz);
        double stride = p.isSprinting() ? 0.95 : 0.7;
        if (walked < stride) return;
        walked = 0;
        left = !left;
        double yaw = Math.toRadians(p.getYRot());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double off = (left ? -1 : 1) * 0.14;
        head = (head + 1) % MAX;
        sx[head] = p.getX() + rx * off;
        sy[head] = p.getY() + 0.015;
        sz[head] = p.getZ() + rz * off;
        syaw[head] = (float) yaw;
        st[head] = System.nanoTime();
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.nanoTime();
        long lifeNs = (long) (life.getF() * 1e9);
        float k = intensity.getF() / 100f;
        d.glowBegin();
        for (int i = 0; i < MAX; i++) {
            if (st[i] == 0) continue;
            long age = now - st[i];
            if (age > lifeNs) {
                st[i] = 0;
                continue;
            }
            float t = age / (float) lifeNs;
            // вспышка в первые доли секунды, затем мягкое затухание
            float a = t < 0.08f ? t / 0.08f : (float) Math.pow(1f - (t - 0.08f) / 0.92f, 1.6);
            int c = Colors.withAlpha(Palette.color(preset.getId(), 0.15f + t * 0.5f, i * 0.1f, color.get(), color2.get()), Math.round(170 * a * k));
            footprint(d, sx[i], sy[i], sz[i], syaw[i], c);
        }
        d.glowEnd();
    }

    /** Стопа — вытянутое мягкое пятно (эллипс), повёрнутое по направлению шага. */
    private static void footprint(WorldDraw d, double x, double y, double z, float yaw, int c) {
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        double len = 0.16, wid = 0.07;
        int e = c & 0xFFFFFF, n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double u0 = Math.cos(a0) * wid, v0 = Math.sin(a0) * len, u1 = Math.cos(a1) * wid, v1 = Math.sin(a1) * len;
            d.quad(x, y, z, x + rx * u0 + fx * v0, y, z + rz * u0 + fz * v0, x + rx * u1 + fx * v1, y, z + rz * u1 + fz * v1, x, y, z, c, e, e, c);
        }
    }
}
