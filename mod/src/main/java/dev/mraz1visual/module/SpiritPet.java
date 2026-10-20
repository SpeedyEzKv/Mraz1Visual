package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Магический питомец: огонёк со шлейфом, парящий кристалл, куб-компаньон или три сферы.
 * Нарисован эффектами, а не мобом — летает за плечом и виден только тебе.
 */
public final class SpiritPet extends Module implements WorldLayer {
    private static final long START = System.nanoTime();
    private static final int TRAIL = 18;

    final EnumSetting kind = add(new EnumSetting("kind", "Питомец", 0,
            "wisp", "Огонёк", "crystal", "Кристалл", "cube", "Куб-компаньон", "orbs", "Три сферы", "star", "Звёздочка"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFB8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFF7FD8FF));
    final EnumSetting side = add(new EnumSetting("side", "Где", 0, "right", "Справа", "left", "Слева", "back", "Сзади"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));

    private double px, py, pz;
    private boolean placed;
    private long lastFrame;
    private final double[][] trail = new double[TRAIL][3];
    private int trailHead;
    private float trailTimer;

    public SpiritPet() {
        super("spirit_pet", "Spirit Pet", "Волшебный питомец-огонёк, кристалл или куб. Виден только тебе.", Category.PETS);
    }

    @Override
    protected void onDisable() { placed = false; }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float time = (now - START) / 1e9f;

        double ex = p.xo + (p.getX() - p.xo) * pt, ey = p.yo + (p.getY() - p.yo) * pt, ez = p.zo + (p.getZ() - p.zo) * pt;
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        double off = switch (side.getId()) { case "left" -> -1; case "back" -> 0; default -> 1; };
        double back = side.getId().equals("back") ? 1.1 : 0.35;
        double tx = ex + rx * off * 0.85 - fx * back, tz = ez + rz * off * 0.85 - fz * back;
        double ty = ey + 1.55 + Math.sin(time * 1.8) * 0.12;
        if (!placed) {
            px = tx; py = ty; pz = tz;
            placed = true;
            for (double[] t : trail) { t[0] = px; t[1] = py; t[2] = pz; }
        }
        double k = Math.min(1, dt * 6);
        px += (tx - px) * k;
        py += (ty - py) * k;
        pz += (tz - pz) * k;

        trailTimer += dt;
        if (trailTimer > 0.025f) {
            trailTimer = 0;
            trailHead = (trailHead + 1) % TRAIL;
            trail[trailHead][0] = px; trail[trailHead][1] = py; trail[trailHead][2] = pz;
        }

        double s = size.get();
        int c1 = color.get(), c2 = color2.get();
        switch (kind.getId()) {
            case "crystal" -> {
                double bob = Math.sin(time * 2.2) * 0.05;
                d.crystal(px, py - 0.12 * s + bob, pz, 0.11 * s, 0.24 * s, time * 1.4, c2, Colors.mulAlpha(c1, 0.8f));
                d.billboardDot(px, py + bob, pz, 0.3 * s, Colors.mulAlpha(c1, 0.18f));
                for (int i = 0; i < 3; i++) {
                    double a = time * 2.5 + i * Math.PI * 2 / 3;
                    d.billboardDot(px + Math.cos(a) * 0.22 * s, py + bob + Math.sin(time * 3 + i) * 0.06, pz + Math.sin(a) * 0.22 * s,
                            0.03 * s, Colors.mulAlpha(c2, 0.9f));
                }
            }
            case "cube" -> {
                d.cube(px, py, pz, 0.11 * s, time * 1.1, time * 0.7, Colors.mulAlpha(c1, 0.55f), c2);
                d.billboardDot(px, py, pz, 0.28 * s, Colors.mulAlpha(c1, 0.15f));
                drawTrail(d, c1, 0.05 * s);
            }
            case "orbs" -> {
                for (int i = 0; i < 3; i++) {
                    double a = time * 2 + i * Math.PI * 2 / 3;
                    double ox = px + Math.cos(a) * 0.18 * s, oz = pz + Math.sin(a) * 0.18 * s, oy = py + Math.sin(time * 2.4 + i * 2) * 0.07;
                    int c = i == 1 ? c2 : c1;
                    d.billboardDot(ox, oy, oz, 0.07 * s, Colors.mulAlpha(c, 0.95f));
                    d.billboardDot(ox, oy, oz, 0.16 * s, Colors.mulAlpha(c, 0.2f));
                }
            }
            case "star" -> {
                double r = 0.16 * s * (1 + 0.1 * Math.sin(time * 5));
                d.billboardCorners(px, py, pz, r, time * 2, 0.035 * s, Colors.mulAlpha(c2, 0.95f));
                d.billboardDot(px, py, pz, 0.06 * s, 0xFFFFFFFF);
                d.billboardDot(px, py, pz, 0.26 * s, Colors.mulAlpha(c1, 0.2f));
                drawTrail(d, c2, 0.04 * s);
            }
            default -> {
                // огонёк: яркое ядро, ореол и длинный шлейф
                float flick = 0.85f + 0.15f * (float) Math.sin(time * 13);
                d.billboardDot(px, py, pz, 0.075 * s * flick, 0xFFFFFFFF);
                d.billboardDot(px, py, pz, 0.15 * s, Colors.mulAlpha(c1, 0.7f));
                d.billboardDot(px, py, pz, 0.32 * s, Colors.mulAlpha(c2, 0.18f));
                drawTrail(d, c1, 0.08 * s);
            }
        }
    }

    private void drawTrail(WorldDraw d, int c, double r) {
        for (int i = 1; i < TRAIL; i++) {
            double[] t = trail[(trailHead - i + TRAIL) % TRAIL];
            float f = 1f - i / (float) TRAIL;
            d.billboardDot(t[0], t[1], t[2], r * f, Colors.mulAlpha(c, f * 0.6f));
        }
    }
}
