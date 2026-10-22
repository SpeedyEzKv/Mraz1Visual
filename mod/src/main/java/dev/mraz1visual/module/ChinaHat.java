package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Шляпа-конус над головой: полупрозрачный градиент от почти прозрачной вершины к краю, мягкая кромка
 * и тонкий светлый обод. Наклоняется вместе с головой и мягко покачивается от движения. Видна от 3-го лица.
 */
public final class ChinaHat extends Module implements WorldLayer {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting base = add(new ColorSetting("base", "Свой: край", 0xFF7A6BFF));
    final ColorSetting tip = add(new ColorSetting("tip", "Свой: верх", 0xFFFFFFFF));
    final SliderSetting radius = add(new SliderSetting("radius", "Ширина", 0.62, 0.3, 1.2, 0.05, " бл."));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.28, 0.1, 0.6, 0.02, " бл."));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 70, 15, 100, 5, " %"));
    final BoolSetting rim = add(new BoolSetting("rim", "Светящийся обод", true));
    final BoolSetting sway = add(new BoolSetting("sway", "Покачивание", true));

    private double swayX, swayZ, swayVX, swayVZ;
    private long last;

    public ChinaHat() {
        super("china_hat", "China Hat", "Полупрозрачная шляпа-конус: градиент, мягкий край, покачивание.", Category.COSMETICS);
    }

    private int col(float t, float a) {
        // свой цвет: от «верха» к «краю»
        String pr = preset.getId();
        return Fx.color(pr, t, Fx.time() * 0.3f, tip.get(), base.get(), a * opacity.getF() / 100f);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        Vec3 pos = p.getPosition(pt);
        double eye = p.getEyeHeight();
        double x = pos.x, y = pos.y + eye, z = pos.z;
        // наклон вместе с головой: ось конуса — «вверх» головы
        double yaw = Math.toRadians(p.getViewYRot(pt)), pitch = Math.toRadians(p.getViewXRot(pt));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double ux = fx * Math.sin(pitch), uy = Math.cos(pitch), uz = fz * Math.sin(pitch);
        // покачивание: пружина от горизонтальной скорости (отстаёт и догоняет)
        if (sway.get()) {
            var v = p.getDeltaMovement();
            double tx = -v.x * 1.6, tz = -v.z * 1.6;
            swayVX += ((tx - swayX) * 60 - swayVX * 9) * dt;
            swayVZ += ((tz - swayZ) * 60 - swayVZ * 9) * dt;
            swayX += swayVX * dt;
            swayZ += swayVZ * dt;
            ux += swayX;
            uz += swayZ;
        }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        // база конуса — над макушкой (0.3 блока над глазами вдоль оси головы)
        double bx = x + ux * 0.32, by = y + uy * 0.32, bz = z + uz * 0.32;
        // базис в плоскости основания
        double ax = 1, ay = 0, az = 0;
        double e1x = ay * uz - az * uy, e1y = az * ux - ax * uz, e1z = ax * uy - ay * ux;
        double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l1; e1y /= l1; e1z /= l1;
        double e2x = uy * e1z - uz * e1y, e2y = uz * e1x - ux * e1z, e2z = ux * e1y - uy * e1x;
        double r = radius.get(), h = height.get();
        double apx = bx + ux * h, apy = by + uy * h, apz = bz + uz * h;
        int n = Fx.segments(56);
        int cTip = col(0.05f, 0.12f), cMid = col(0.45f, 0.4f), cEdge = col(0.75f, 0.7f);
        double px0 = 0, py0 = 0, pz0 = 0;
        for (int i = 0; i <= n; i++) {
            double a = i * Math.PI * 2 / n, c = Math.cos(a), s = Math.sin(a);
            double ex = bx + (e1x * c + e2x * s) * r, ey = by + (e1y * c + e2y * s) * r, ez = bz + (e1z * c + e2z * s) * r;
            if (i > 0) {
                // две полосы: от края к середине и от середины к вершине — градиент без «пластика»
                double m0x = (px0 + apx) / 2, m0y = (py0 + apy) / 2, m0z = (pz0 + apz) / 2;
                double m1x = (ex + apx) / 2, m1y = (ey + apy) / 2, m1z = (ez + apz) / 2;
                d.quad(px0, py0, pz0, ex, ey, ez, m1x, m1y, m1z, m0x, m0y, m0z, cEdge, cEdge, cMid, cMid);
                d.quad(m0x, m0y, m0z, m1x, m1y, m1z, apx, apy, apz, apx, apy, apz, cMid, cMid, cTip, cTip);
                if (rim.get()) d.line(px0, py0, pz0, ex, ey, ez, 0.016, col(0.15f, 0.95f));
            }
            px0 = ex; py0 = ey; pz0 = ez;
        }
        if (rim.get()) {
            // мягкое свечение обода
            d.glowBegin();
            for (int i = 1; i <= n; i++) {
                double a0 = (i - 1) * Math.PI * 2 / n, a1 = i * Math.PI * 2 / n;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                d.line(bx + (e1x * c0 + e2x * s0) * r, by + (e1y * c0 + e2y * s0) * r, bz + (e1z * c0 + e2z * s0) * r,
                        bx + (e1x * c1 + e2x * s1) * r, by + (e1y * c1 + e2y * s1) * r, bz + (e1z * c1 + e2z * s1) * r,
                        0.09, col(0.4f, 0.2f));
            }
            d.glowEnd();
        }
    }
}
