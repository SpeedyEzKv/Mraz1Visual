package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Объёмные примитивы для своих моделей (питомцы, крылья, мечи в мире): повёрнутый параллелепипед
 * с мягким затенением граней. Без аллокаций — оси считаются в поля экземпляра.
 */
public final class Shapes {
    // базис детали: X — вправо, Y — вверх, Z — вперёд
    private double xx, xy, xz, yx, yy, yz, zx, zy, zz;

    /** Повернуть базис: рыскание (вокруг Y), тангаж (вокруг X), крен (вокруг Z), в радианах. */
    public Shapes orient(double yaw, double pitch, double roll) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
        // R = Ry(yaw) * Rx(pitch) * Rz(roll); столбцы — оси детали в мире (вперёд при yaw=0 — +Z)
        xx = cy * cr + sy * sp * sr; xy = cp * sr; xz = -sy * cr + cy * sp * sr;
        yx = -cy * sr + sy * sp * cr; yy = cp * cr; yz = sy * sr + cy * sp * cr;
        zx = sy * cp; zy = -sp; zz = cy * cp;
        return this;
    }

    /** Локальная точка детали → смещение в мире. */
    public double ox(double x, double y, double z) { return xx * x + yx * y + zx * z; }
    public double oy(double x, double y, double z) { return xy * x + yy * y + zy * z; }
    public double oz(double x, double y, double z) { return xz * x + yz * y + zz * z; }

    /**
     * Параллелепипед с центром (cx, cy, cz) в мире и полуразмерами hx, hy, hz по осям детали.
     * Верх светлее, низ темнее — объём читается без освещения.
     */
    public void box(WorldDraw d, double cx, double cy, double cz, double hx, double hy, double hz, int color) {
        double ax = xx * hx, ay = xy * hx, az = xz * hx;
        double bx = yx * hy, by = yy * hy, bz = yz * hy;
        double fx = zx * hz, fy = zy * hz, fz = zz * hz;
        int top = color, side1 = Colors.shade(color, 0.82f), side2 = Colors.shade(color, 0.7f), bottom = Colors.shade(color, 0.55f);
        // верх (+Y)
        face(d, cx + bx, cy + by, cz + bz, ax, ay, az, fx, fy, fz, top);
        // низ (-Y)
        face(d, cx - bx, cy - by, cz - bz, ax, ay, az, fx, fy, fz, bottom);
        // право/лево (±X)
        face(d, cx + ax, cy + ay, cz + az, bx, by, bz, fx, fy, fz, side1);
        face(d, cx - ax, cy - ay, cz - az, bx, by, bz, fx, fy, fz, side1);
        // перед/зад (±Z)
        face(d, cx + fx, cy + fy, cz + fz, ax, ay, az, bx, by, bz, side2);
        face(d, cx - fx, cy - fy, cz - fz, ax, ay, az, bx, by, bz, side2);
    }

    /** Грань: центр + два полувектора. */
    private static void face(WorldDraw d, double cx, double cy, double cz, double ux, double uy, double uz,
                             double vx, double vy, double vz, int c) {
        d.quad(cx - ux - vx, cy - uy - vy, cz - uz - vz, cx + ux - vx, cy + uy - vy, cz + uz - vz,
                cx + ux + vx, cy + uy + vy, cz + uz + vz, cx - ux + vx, cy - uy + vy, cz - uz + vz, c, c, c, c);
    }

    /** Плоская пластина (двусторонний квад) в плоскости X–Z детали. */
    public void plate(WorldDraw d, double cx, double cy, double cz, double hx, double hz, int color) {
        double ax = xx * hx, ay = xy * hx, az = xz * hx, fx = zx * hz, fy = zy * hz, fz = zz * hz;
        face(d, cx, cy, cz, ax, ay, az, fx, fy, fz, color);
    }
}
