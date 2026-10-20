package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.Shapes;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Свои мультяшные питомцы (оригинальные, ни на кого не похожи): дракончик, призрачный котик, огненный лис,
 * летающий слизень, робот-дрон и звёздная медуза. Собраны из объёмных блоков, двигаются плавно по времени кадра,
 * у каждого свои idle-анимации и лёгкое свечение. Не сущность и не пакет — только картинка у тебя.
 */
public final class Pets extends Module implements WorldLayer {
    final EnumSetting kind = add(new EnumSetting("kind", "Питомец", 0,
            "dragon", "Дракончик", "cat", "Призрачный котик", "fox", "Огненный лис", "slime", "Летающий слизень",
            "drone", "Робот-дрон", "jelly", "Звёздная медуза"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 0, "preset", "Как у питомца", "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFB8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFF7FD8FF));
    final EnumSetting side = add(new EnumSetting("side", "Где", 0, "right", "Справа", "left", "Слева", "back", "Сзади"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.6, 2, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость анимации", 1, 0.3, 3, 0.05, "×"));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 95, 20, 100, 5, " %"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 100, 20, 100, 5, " %"));
    final BoolSetting glow = add(new BoolSetting("glow", "Свечение", true));

    private final Shapes sh = new Shapes();
    private double x, y, z, yaw, vx, vy, vz;
    private boolean placed;
    private long last;
    private float t;          // время анимации, с
    private float moving;     // 0..1 — насколько питомец сейчас движется
    private double s;         // масштаб
    private double cyaw;      // для перевода локальных координат

    public Pets() {
        super("pets", "Pets", "Милые питомцы: дракончик, котик, лис, слизень, дрон, медуза.", Category.PETS);
    }

    @Override
    protected void onDisable() { placed = false; }

    // ── цвета ────────────────────────────────────────────────

    /** Основной цвет: пресет питомца или свой (один / градиент / радуга), с яркостью и прозрачностью. */
    private int main(int preset, float phase) {
        int c = colorMode.getId().equals("preset") ? preset : ColorFx.pick(colorMode.getId(), color.get(), color2.get(), phase);
        return fin(c);
    }

    private int fin(int c) {
        c = Colors.shade(c, brightness.getF() / 100f);
        return Colors.mulAlpha(c, opacity.getF() / 100f);
    }

    // ── движение ─────────────────────────────────────────────

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        t += dt * speed.getF();
        s = 0.9 * size.get();

        double ex = p.xo + (p.getX() - p.xo) * pt, ey = p.yo + (p.getY() - p.yo) * pt, ez = p.zo + (p.getZ() - p.zo) * pt;
        double pyaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(pyaw), fz = Math.cos(pyaw), rx = -fz, rz = fx;
        double off = switch (side.getId()) { case "left" -> -1; case "back" -> 0; default -> 1; };
        double back = side.getId().equals("back") ? 1.2 : 0.45;
        boolean walker = kind.getId().equals("fox");
        double tx = ex + rx * off * 0.95 - fx * back, tz = ez + rz * off * 0.95 - fz * back;
        double ty = walker && p.onGround() ? ey + 0.11 * s : ey + 1.35 + Math.sin(t * 1.7) * 0.08;
        if (!placed) {
            x = tx; y = ty; z = tz; yaw = -pyaw;
            vx = vy = vz = 0;
            placed = true;
        }
        // мягкая «пружина» с затуханием — без рывков на любом FPS
        double stiff = 26, damp = 9;
        vx += ((tx - x) * stiff - vx * damp) * dt;
        vy += ((ty - y) * stiff - vy * damp) * dt;
        vz += ((tz - z) * stiff - vz * damp) * dt;
        x += vx * dt; y += vy * dt; z += vz * dt;
        // если отстал (телепорт) — сразу рядом
        if ((tx - x) * (tx - x) + (tz - z) * (tz - z) > 64) { x = tx; y = ty; z = tz; vx = vy = vz = 0; }

        double hv = Math.sqrt(vx * vx + vz * vz);
        moving += ((hv > 0.4 ? 1f : 0f) - moving) * Math.min(1f, dt * 5f);
        // поворот: по ходу движения, а стоя — туда же, куда смотрит игрок
        double want = hv > 0.4 ? Math.atan2(vx, vz) : -pyaw;
        double dy = Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
        yaw += dy * Math.min(1, dt * 6);
        cyaw = yaw;

        if (glow.get()) {
            d.glowBegin();
            d.billboardDot(x, y + 0.03 * s, z, 0.32 * s, Colors.withAlpha(main(glowPreset(), 0f), Math.round(30 * opacity.getF() / 100f)), 16);
            d.glowEnd();
        }
        switch (kind.getId()) {
            case "cat" -> cat(d);
            case "fox" -> fox(d);
            case "slime" -> slime(d);
            case "drone" -> drone(d);
            case "jelly" -> jelly(d);
            default -> dragon(d);
        }
    }

    private int glowPreset() {
        return switch (kind.getId()) {
            case "cat" -> 0xFFB8D8FF;
            case "fox" -> 0xFFFF8A3A;
            case "slime" -> 0xFF7BE07A;
            case "drone" -> 0xFF4FD8FF;
            case "jelly" -> 0xFFFF8AD8;
            default -> 0xFF5FD49A;
        };
    }

    // ── локальные координаты питомца: x — вправо, y — вверх, z — вперёд ──

    private double wx(double lx, double ly, double lz) { return x + (Math.cos(cyaw) * lx + Math.sin(cyaw) * lz) * s; }
    private double wy(double lx, double ly, double lz) { return y + ly * s; }
    private double wz(double lx, double ly, double lz) { return z + (-Math.sin(cyaw) * lx + Math.cos(cyaw) * lz) * s; }

    /** Деталь-блок: центр в локальных координатах, полуразмеры, свой наклон (тангаж/крен) и доворот. */
    private void part(WorldDraw d, double lx, double ly, double lz, double hx, double hy, double hz, int c,
                      double pitch, double roll, double yawOff) {
        sh.orient(cyaw + yawOff, pitch, roll);
        sh.box(d, wx(lx, ly, lz), wy(lx, ly, lz), wz(lx, ly, lz), hx * s, hy * s, hz * s, c);
    }

    private void part(WorldDraw d, double lx, double ly, double lz, double hx, double hy, double hz, int c) {
        part(d, lx, ly, lz, hx, hy, hz, c, 0, 0, 0);
    }

    /** Пара глаз с бликом. ex — отступ от центра, z — передняя грань головы. */
    private void eyes(WorldDraw d, double ex, double ey, double ez, double r, boolean glowing, int glowColor) {
        float blink = (t % 4.2f) < 0.12f ? 0.15f : 1f; // моргание
        for (int sd = -1; sd <= 1; sd += 2) {
            part(d, sd * ex, ey, ez, r, r * blink, 0.006, fin(0xFF14141A));
            part(d, sd * ex + r * 0.35, ey + r * 0.35 * blink, ez + 0.004, r * 0.3, r * 0.3 * blink, 0.004, fin(0xFFFFFFFF));
            if (glowing) {
                d.glowBegin();
                d.billboardDot(wx(sd * ex, ey, ez + 0.01), wy(sd * ex, ey, ez + 0.01), wz(sd * ex, ey, ez + 0.01), r * 2.6 * s, Colors.withAlpha(glowColor, 120), 8);
                d.glowEnd();
            }
        }
    }

    // ── дракончик ────────────────────────────────────────────

    private void dragon(WorldDraw d) {
        int body = main(0xFF5FD49A, 0f), belly = fin(0xFFF2E6A0), horn = fin(0xFFF4F0E0), wing = Colors.mulAlpha(main(0xFF2FA878, 0.4f), 0.9f);
        double bob = Math.sin(t * 3.2) * 0.015;
        part(d, 0, bob, 0, 0.11, 0.09, 0.15, body);
        part(d, 0, bob - 0.035, 0.015, 0.088, 0.06, 0.125, belly);
        double look = Math.sin(t * 0.7) * 0.35 * (1 - moving);
        double hy = 0.11 + bob, hz = 0.17;
        part(d, 0, hy, hz, 0.09, 0.08, 0.085, body, Math.sin(t * 1.1) * 0.08, 0, look);
        part(d, Math.sin(look) * 0.09, hy - 0.03, hz + 0.1, 0.058, 0.042, 0.05, body, 0, 0, look);
        for (int sd = -1; sd <= 1; sd += 2) part(d, sd * 0.05, hy + 0.1, hz - 0.03, 0.014, 0.04, 0.014, horn, -0.5, sd * 0.25, look);
        eyes(d, 0.045, hy + 0.02, hz + 0.088, 0.018, false, 0);
        // крылья: машут, на ходу — чаще
        double flap = Math.sin(t * (6 + moving * 6)) * (0.5 + moving * 0.3);
        for (int sd = -1; sd <= 1; sd += 2) {
            double roll = sd * (0.35 + flap);
            sh.orient(cyaw, 0, roll);
            double px = wx(sd * 0.1, bob + 0.07, 0), py = wy(sd * 0.1, bob + 0.07, 0), pz = wz(sd * 0.1, bob + 0.07, 0);
            double ox = sh.ox(sd * 0.15 * s, 0, 0), oy = sh.oy(sd * 0.15 * s, 0, 0), oz = sh.oz(sd * 0.15 * s, 0, 0);
            sh.plate(d, px + ox, py + oy, pz + oz, 0.15 * s, 0.1 * s, wing);
        }
        // хвост качается
        for (int i = 0; i < 4; i++) {
            double sway = Math.sin(t * 3 - i * 0.8) * 0.035 * (i + 1);
            double w = 0.06 - i * 0.012;
            part(d, sway, bob - 0.01 - i * 0.03, -0.18 - i * 0.085, w, w * 0.85, 0.05, i == 3 ? horn : body, 0.25, 0, sway * 2);
        }
        for (int i = 0; i < 4; i++) part(d, (i % 2 == 0 ? -1 : 1) * 0.07, bob - 0.1, (i < 2 ? 1 : -1) * 0.08, 0.025, 0.025, 0.03, body);
    }

    // ── призрачный котик ─────────────────────────────────────

    private void cat(WorldDraw d) {
        int body = Colors.mulAlpha(main(0xFFE6E2FF, 0f), 0.62f), ear = Colors.mulAlpha(main(0xFFC8C0FF, 0.3f), 0.7f);
        double bob = Math.sin(t * 2.2) * 0.03;
        part(d, 0, bob, 0, 0.1, 0.075, 0.14, body);
        double look = Math.sin(t * 0.6) * 0.4 * (1 - moving);
        part(d, 0, bob + 0.08, 0.15, 0.085, 0.075, 0.075, body, 0, 0, look);
        for (int sd = -1; sd <= 1; sd += 2) part(d, sd * 0.05, bob + 0.17, 0.15, 0.022, 0.032, 0.012, ear, 0, sd * -0.3, look);
        eyes(d, 0.04, bob + 0.09, 0.226, 0.016, true, main(0xFF7FE8FF, 0.5f));
        // хвост волной вверх
        for (int i = 0; i < 5; i++) {
            double w = Math.sin(t * 2.6 - i * 0.7) * 0.03;
            part(d, w, bob + 0.02 + i * 0.045, -0.15 - i * 0.03, 0.022, 0.03, 0.022, body, -0.6, 0, 0);
        }
        // «дымка» вместо лап
        d.glowBegin();
        for (int i = 0; i < 6; i++) {
            float f = (t * 0.6f + i / 6f) % 1f;
            d.billboardDot(wx(Math.sin(i * 2.1) * 0.06, bob - 0.08 - f * 0.18, Math.cos(i * 1.7) * 0.08),
                    wy(0, bob - 0.08 - f * 0.18, 0), wz(Math.sin(i * 2.1) * 0.06, bob - 0.08 - f * 0.18, Math.cos(i * 1.7) * 0.08),
                    0.05 * s * (1 - f), Colors.withAlpha(main(0xFFB8D8FF, f), Math.round(90 * (1 - f) * opacity.getF() / 100f)), 8);
        }
        d.glowEnd();
    }

    // ── огненный лис ─────────────────────────────────────────

    private void fox(WorldDraw d) {
        int fur = main(0xFFFF8A3A, 0f), white = fin(0xFFFFF4E6), dark = fin(0xFF2A1A14);
        double step = Math.sin(t * 10) * moving;
        double bob = Math.abs(step) * 0.02;
        part(d, 0, bob, 0, 0.1, 0.075, 0.15, fur);
        part(d, 0, bob - 0.01, 0.13, 0.07, 0.06, 0.03, white);
        double look = Math.sin(t * 0.8) * 0.35 * (1 - moving);
        part(d, 0, bob + 0.08, 0.17, 0.085, 0.07, 0.07, fur, 0, 0, look);
        part(d, Math.sin(look) * 0.08, bob + 0.055, 0.25, 0.04, 0.03, 0.045, white, 0, 0, look);
        part(d, Math.sin(look) * 0.12, bob + 0.065, 0.296, 0.012, 0.01, 0.006, dark, 0, 0, look);
        for (int sd = -1; sd <= 1; sd += 2) {
            part(d, sd * 0.05, bob + 0.16, 0.16, 0.026, 0.04, 0.012, fur, 0, sd * -0.15, look);
            part(d, sd * 0.05, bob + 0.2, 0.16, 0.012, 0.012, 0.013, dark, 0, sd * -0.15, look);
        }
        eyes(d, 0.042, bob + 0.095, 0.241, 0.014, false, 0);
        // лапы шагают
        for (int i = 0; i < 4; i++) {
            double sw = (i == 0 || i == 3 ? step : -step) * 0.03;
            part(d, (i % 2 == 0 ? -1 : 1) * 0.06, bob - 0.1, (i < 2 ? 1 : -1) * 0.09 + sw, 0.022, 0.035, 0.022, dark);
        }
        // пышный хвост с огненным кончиком
        double tipX = 0, tipY = 0, tipZ = 0;
        for (int i = 0; i < 4; i++) {
            double sway = Math.sin(t * 2.4 - i * 0.6) * 0.03 * (i + 1);
            double w = 0.05 + (i == 2 ? 0.02 : 0.01);
            double ly = bob + 0.02 + i * 0.03, lz = -0.17 - i * 0.075;
            part(d, sway, ly, lz, w, w, 0.05, i == 3 ? white : fur, -0.35, 0, sway * 2);
            tipX = sway; tipY = ly + 0.04; tipZ = lz - 0.04;
        }
        d.glowBegin();
        for (int i = 0; i < 7; i++) {
            float f = (t * 1.4f + i / 7f) % 1f;
            double jx = tipX + Math.sin(i * 2.3 + t * 3) * 0.02, jy = tipY + f * 0.16, jz = tipZ + Math.cos(i * 1.9) * 0.02;
            int c = Colors.lerp(0xFFFFF0B0, main(0xFFFF5A1A, f), f);
            d.billboardDot(wx(jx, jy, jz), wy(jx, jy, jz), wz(jx, jy, jz), 0.045 * s * (1 - f * 0.7),
                    Colors.withAlpha(c, Math.round(170 * (1 - f) * opacity.getF() / 100f)), 8);
        }
        d.glowEnd();
    }

    // ── летающий слизень ─────────────────────────────────────

    private void slime(WorldDraw d) {
        double sq = Math.sin(t * 4) * 0.12;
        double hy = 0.12 * (1 + sq), hx = 0.12 * (1 - sq * 0.5);
        int shell = Colors.mulAlpha(main(0xFF7BE07A, 0f), 0.55f), core = main(0xFF3FA84A, 0.4f);
        part(d, 0, 0, 0, hx * 0.55, hy * 0.55, hx * 0.55, core);
        eyes(d, 0.045, 0.03 * (1 + sq), hx + 0.002, 0.02, false, 0);
        part(d, 0, -0.03 * (1 + sq), hx + 0.002, 0.03, 0.008, 0.004, fin(0xFF14141A));
        part(d, 0, 0, 0, hx, hy, hx, shell);
        double flap = Math.sin(t * 14) * 0.6;
        for (int sd = -1; sd <= 1; sd += 2) {
            sh.orient(cyaw, 0, sd * (0.3 + flap));
            double px = wx(sd * hx, 0.05, -0.02), py = wy(sd * hx, 0.05, -0.02), pz = wz(sd * hx, 0.05, -0.02);
            sh.plate(d, px + sh.ox(sd * 0.07 * s, 0, 0), py + sh.oy(sd * 0.07 * s, 0, 0), pz + sh.oz(sd * 0.07 * s, 0, 0),
                    0.07 * s, 0.05 * s, fin(0xD0FFFFFF));
        }
    }

    // ── робот-дрон ───────────────────────────────────────────

    private void drone(WorldDraw d) {
        int metal = main(0xFF8A93A6, 0f), darkm = fin(0xFF3A3F4A), led = main(0xFF4FD8FF, 0.5f);
        double tilt = Math.max(-0.35, Math.min(0.35, (vx * Math.sin(cyaw) + vz * Math.cos(cyaw)) * 0.08));
        double hover = Math.sin(t * 2.5) * 0.02;
        part(d, 0, hover, 0, 0.12, 0.045, 0.12, metal, tilt, 0, 0);
        part(d, 0, hover + 0.06, 0, 0.07, 0.025, 0.07, darkm, tilt, 0, 0);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            double ax = Math.sin(a) * 0.17, az = Math.cos(a) * 0.17;
            part(d, ax * 0.6, hover + 0.01, az * 0.6, 0.06, 0.012, 0.015, darkm, 0, 0, a);
            part(d, ax, hover + 0.03, az, 0.018, 0.02, 0.018, metal);
            // винты крутятся, вокруг — полупрозрачный «диск»
            double spin = t * 40 + i;
            for (int b = 0; b < 2; b++) {
                sh.orient(cyaw + spin + b * Math.PI / 2, 0, 0);
                sh.plate(d, wx(ax, hover + 0.055, az), wy(ax, hover + 0.055, az), wz(ax, hover + 0.055, az), 0.075 * s, 0.01 * s, darkm);
            }
            d.ring(wx(ax, hover + 0.057, az), wy(ax, hover + 0.057, az), wz(ax, hover + 0.057, az), 0, 0.08 * s, 20,
                    Colors.withAlpha(metal, 30), Colors.withAlpha(metal, 60));
        }
        // «глаз» и мигающая антенна
        part(d, 0, hover, 0.121, 0.035, 0.02, 0.004, fin(0xFF0E1016));
        d.glowBegin();
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 3);
        d.billboardDot(wx(0, hover, 0.13), wy(0, hover, 0.13), wz(0, hover, 0.13), 0.04 * s, Colors.withAlpha(led, Math.round(220 * pulse)), 10);
        if ((t % 1.6f) < 0.25f) d.billboardDot(wx(0, hover + 0.14, -0.02), wy(0, hover + 0.14, -0.02), wz(0, hover + 0.14, -0.02), 0.03 * s, 0xE0FF4A4A, 8);
        d.glowEnd();
        part(d, 0, hover + 0.1, -0.02, 0.006, 0.04, 0.006, darkm);
    }

    // ── звёздная медуза ──────────────────────────────────────

    private void jelly(WorldDraw d) {
        int bell = main(0xFFFF8AD8, 0f), inner = main(0xFFB8B4F2, 0.5f);
        double pulse = Math.sin(t * 2.2);
        double baseY = y + 0.02 * s + pulse * 0.02 * s;
        double R = 0.17 * s * (1 + pulse * 0.08);
        // купол из слоёв-дисков
        for (int i = 0; i < 6; i++) {
            double f = i / 6.0;
            double r = R * Math.sqrt(1 - f * f);
            int c = Colors.withAlpha(bell, Math.round((70 + i * 18) * opacity.getF() / 100f));
            d.ring(x, baseY + f * 0.14 * s, z, 0, r, 28, Colors.withAlpha(inner, Math.round(40 * opacity.getF() / 100f)), c);
        }
        // щупальца волнами
        for (int k = 0; k < 7; k++) {
            double a = k * Math.PI * 2 / 7 + t * 0.2;
            double px = x + Math.cos(a) * R * 0.75, pz = z + Math.sin(a) * R * 0.75, py = baseY;
            for (int j = 0; j < 6; j++) {
                double w = Math.sin(t * 3 - j * 0.7 + k) * 0.025 * s;
                double nx = x + Math.cos(a) * (R * 0.75 + w), nz = z + Math.sin(a) * (R * 0.75 + w), ny = py - 0.05 * s;
                d.line(px, py, pz, nx, ny, nz, 0.012 * s, Colors.withAlpha(bell, Math.round((200 - j * 30) * opacity.getF() / 100f)));
                px = nx; py = ny; pz = nz;
            }
        }
        // звёздочки внутри
        d.glowBegin();
        for (int i = 0; i < 5; i++) {
            double a = t * 0.8 + i * 1.3;
            double sx = x + Math.cos(a) * R * 0.45, sz = z + Math.sin(a) * R * 0.45, sy = baseY + 0.05 * s + Math.sin(t * 2 + i) * 0.02 * s;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 5 + i * 2);
            d.billboardDot(sx, sy, sz, 0.025 * s, Colors.withAlpha(0xFFFFF4C8, Math.round(230 * tw)), 6);
        }
        d.billboardDot(x, baseY + 0.06 * s, z, 0.12 * s, Colors.withAlpha(bell, 60), 14);
        d.glowEnd();
    }
}
