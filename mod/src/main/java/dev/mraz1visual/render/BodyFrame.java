package dev.mraz1visual.render;

import net.minecraft.client.player.LocalPlayer;

/**
 * Система координат части тела игрока для косметики: точка опоры + оси «вправо», «вверх», «вперёд».
 * Голова учитывает поворот и наклон головы, спина — поворот тела.
 */
public final class BodyFrame {
    public double ox, oy, oz;
    public double rx, ry, rz, ux, uy, uz, fx, fy, fz;

    /** Голова: опора — шея, оси повёрнуты по взгляду. */
    public BodyFrame head(LocalPlayer p, float pt) {
        base(p, pt);
        oy += p.isCrouching() ? 1.2 : 1.5;
        float yaw = lerpAngle(p.yHeadRotO, p.getYHeadRot(), pt);
        float pitch = p.xRotO + (p.getXRot() - p.xRotO) * pt;
        axes(Math.toRadians(yaw), Math.toRadians(pitch));
        return this;
    }

    /** Спина: опора — между лопатками, оси по телу (без наклона). */
    public BodyFrame body(LocalPlayer p, float pt) {
        base(p, pt);
        oy += p.isCrouching() ? 1.05 : 1.35;
        axes(Math.toRadians(lerpAngle(p.yBodyRotO, p.yBodyRot, pt)), p.isCrouching() ? 0.5 : 0);
        return this;
    }

    private void base(LocalPlayer p, float pt) {
        ox = p.xo + (p.getX() - p.xo) * pt;
        oy = p.yo + (p.getY() - p.yo) * pt;
        oz = p.zo + (p.getZ() - p.zo) * pt;
    }

    private void axes(double yaw, double pitch) {
        double fxh = -Math.sin(yaw), fzh = Math.cos(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        // вперёд с наклоном вниз при pitch > 0
        fx = fxh * cp; fy = -sp; fz = fzh * cp;
        ux = fxh * sp; uy = cp; uz = fzh * sp;
        rx = -fzh; ry = 0; rz = fxh;
    }

    /** Локальная точка (вправо, вверх, вперёд) → мир. */
    public double wx(double r, double u, double f) { return ox + rx * r + ux * u + fx * f; }
    public double wy(double r, double u, double f) { return oy + ry * r + uy * u + fy * f; }
    public double wz(double r, double u, double f) { return oz + rz * r + uz * u + fz * f; }

    private static float lerpAngle(float a, float b, float t) {
        float d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * t;
    }
}
