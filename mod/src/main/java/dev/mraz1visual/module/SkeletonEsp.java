package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Скелет поверх игроков в поле зрения: позвоночник, руки и ноги двигаются вместе с моделью.
 * Только в прямой видимости и с проверкой глубины.
 */
public final class SkeletonEsp extends Module implements WorldLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xF0FFFFFF));
    final ColorSetting friendColor = add(new ColorSetting("friend_color", "Цвет друзей", 0xF060E08A));
    final SliderSetting width = add(new SliderSetting("width", "Толщина", 0.04, 0.02, 0.1, 0.005, ""));
    final BoolSetting onlyVisible = add(new BoolSetting("only_visible", "Только в прямой видимости", true));

    public SkeletonEsp() {
        super("skeleton_esp", "Skeleton ESP", "Линии скелета поверх игроков в поле зрения.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double w = width.get();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isInvisible() || !p.isAlive() || p.distanceToSqr(mc.player) > 64 * 64) continue;
            if (onlyVisible.get() && !mc.player.hasLineOfSight(p)) continue;
            int c = FriendsModule.highlight(p.getName().getString()) ? friendColor.get() : color.get();
            double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
            float body = p.yBodyRotO + (p.yBodyRot - p.yBodyRotO) * pt;
            double yaw = Math.toRadians(body);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // вперёд
            double rx = -fz, rz = fx;                          // вправо
            boolean crouch = p.isCrouching();
            double hip = crouch ? 0.6 : 0.75, shoulder = crouch ? 1.15 : 1.4, neck = crouch ? 1.2 : 1.45, head = crouch ? 1.55 : 1.8;
            double lean = crouch ? 0.25 : 0;
            float pos = p.walkAnimation.position(pt), spd = Math.min(1f, p.walkAnimation.speed(pt));
            double swing = Math.cos(pos * 0.6662) * 1.2 * spd;

            double hx = x - fx * lean * 0.3, hz = z - fz * lean * 0.3;
            double nx = x + fx * lean * 0.4, nz = z + fz * lean * 0.4;
            d.line(hx, y + hip, hz, nx, y + neck, nz, w, c);            // позвоночник
            d.line(nx, y + neck, nz, nx, y + head, nz, w, c);           // шея-голова
            double sx = rx * 0.37, sz = rz * 0.37, px = rx * 0.12, pz = rz * 0.12;
            d.line(nx - sx, y + shoulder, nz - sz, nx + sx, y + shoulder, nz + sz, w, c); // плечи
            d.line(hx - px, y + hip, hz - pz, hx + px, y + hip, hz + pz, w, c);           // таз
            // руки: качаются против ног
            for (int side = -1; side <= 1; side += 2) {
                double a = side * swing * -1;
                double ax = nx + rx * 0.37 * side, az = nz + rz * 0.37 * side;
                double ex = ax + fx * Math.sin(a) * 0.7, ey = y + shoulder - Math.cos(a) * 0.7, ez = az + fz * Math.sin(a) * 0.7;
                d.line(ax, y + shoulder, az, ex, ey, ez, w, c);
                double lx = hx + rx * 0.12 * side, lz = hz + rz * 0.12 * side;
                double b = side * swing;
                double kx = lx + fx * Math.sin(b) * hip, ky = y + hip - Math.cos(b) * hip, kz = lz + fz * Math.sin(b) * hip;
                d.line(lx, y + hip, lz, kx, ky, kz, w, c);
            }
        }
    }
}
