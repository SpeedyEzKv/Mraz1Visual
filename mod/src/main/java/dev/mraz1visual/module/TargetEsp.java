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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Эффект вокруг цели, которую ты бьёшь. Несколько стилей: кольцо с хвостом, призраки,
 * маркер, кристаллы, спираль. Плавно появляется и гаснет. С проверкой глубины.
 */
public final class TargetEsp extends Module implements WorldLayer, AttackListener {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0,
            "ring", "Кольцо", "ghosts", "Призраки", "marker", "Маркер", "crystals", "Кристаллы", "spiral", "Спираль",
            "double", "Два кольца", "base", "Круг у ног"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xE0B8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xE07FD8FF));
    final SliderSetting keep = add(new SliderSetting("keep", "Держать после удара", 3, 1, 10, 0.5, " с"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.3, 3, 0.1, "×"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));

    private LivingEntity target;
    private long hitAt;
    private float fade;
    private long lastFrame;
    private double sx, sy, sz;
    private static final long START = System.nanoTime();

    public TargetEsp() {
        super("target_esp", "Target ESP", "Красивый эффект вокруг цели после твоего удара.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            if (target == null) {
                sx = le.getX();
                sy = le.getY();
                sz = le.getZ();
            }
            target = le;
            hitAt = System.currentTimeMillis();
        }
    }

    private int col(float t, float a) {
        return Colors.mulAlpha(ColorFx.pick(colorMode.getId(), color.get(), color2.get(), t), a);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long nowNs = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        LivingEntity t = target;
        boolean alive = t != null && !t.isRemoved() && !t.isDeadOrDying() && System.currentTimeMillis() - hitAt < keep.get() * 1000;
        fade += (alive ? 1 : -1) * dt * 5f;
        fade = Math.max(0, Math.min(1, fade));
        if (t == null) return;
        if (fade <= 0 && !alive) {
            target = null;
            return;
        }
        // позиция с интерполяцией + мягкое догоняние (без рывков при телепортах цели)
        double tx = t.xo + (t.getX() - t.xo) * pt, ty = t.yo + (t.getY() - t.yo) * pt, tz = t.zo + (t.getZ() - t.zo) * pt;
        double k = Math.min(1, dt * 25);
        sx += (tx - sx) * k;
        sy += (ty - sy) * k;
        sz += (tz - sz) * k;
        double x = sx, y = sy, z = sz;
        float time = (nowNs - START) / 1e9f * speed.getF();
        double h = t.getBbHeight(), r = (t.getBbWidth() * 0.85 + 0.15) * size.get();
        float A = fade;
        switch (style.getId()) {
            case "ghosts" -> {
                // три огонька летают вокруг цели по разным орбитам, за каждым шлейф
                for (int g = 0; g < 3; g++) {
                    for (int i = 0; i < 14; i++) {
                        float tt = time * 2.4f - i * 0.045f;
                        double a = tt + g * Math.PI * 2 / 3;
                        double yy = y + h * (0.5 + 0.35 * Math.sin(tt * 1.3 + g * 2.1));
                        double rr = r * 1.05;
                        float fa = (1f - i / 14f);
                        d.billboardDot(x + Math.cos(a) * rr, yy, z + Math.sin(a) * rr, 0.16 * fa + 0.03, col(g / 3f + i * 0.01f, A * fa * 0.85f));
                        if (i == 0) d.billboardDot(x + Math.cos(a) * rr, yy, z + Math.sin(a) * rr, 0.06, Colors.mulAlpha(0xFFFFFFFF, A));
                    }
                }
            }
            case "marker" -> {
                double cy = y + h * 0.55;
                double s = Math.max(h, r * 2) * 0.42 * (1 + 0.06 * Math.sin(time * 4));
                d.billboardCorners(x, cy, z, s, time * 1.6, 0.06, col(0, A));
                d.billboardCorners(x, cy, z, s * 1.12, time * 1.6, 0.16, col(0.5f, A * 0.18f));
                d.billboardDot(x, cy, z, 0.06, Colors.mulAlpha(0xFFFFFFFF, A * 0.8f));
            }
            case "crystals" -> {
                for (int i = 0; i < 4; i++) {
                    double a = time * 1.2 + i * Math.PI / 2;
                    double yy = y + h * (0.55 + 0.15 * Math.sin(time * 2 + i));
                    double cx = x + Math.cos(a) * r * 1.15, cz = z + Math.sin(a) * r * 1.15;
                    d.crystal(cx, yy, cz, 0.09, 0.17, time * 3 + i, col(i / 4f, A * 0.95f), col(i / 4f + 0.2f, A * 0.55f));
                    d.billboardDot(cx, yy, cz, 0.22, col(i / 4f, A * 0.18f));
                }
            }
            case "spiral" -> {
                int n = 40;
                for (int i = 0; i < n; i++) {
                    float f = i / (float) n;
                    double a = time * 3 + f * Math.PI * 4;
                    double yy = y + h * ((f + time * 0.3) % 1.0);
                    d.billboardDot(x + Math.cos(a) * r, yy, z + Math.sin(a) * r, 0.07, col(f, A * (0.35f + 0.65f * f)));
                }
            }
            case "base" -> {
                d.ring(x, y + 0.02, z, r * 0.7, r * 1.1, 56, col(0, 0.1f * A), col(0, A));
                d.ring(x, y + 0.02, z, r * 1.1, r * 1.25, 56, col(0.5f, A * 0.4f), col(0.5f, 0));
            }
            case "double" -> {
                double y1 = y + h * (0.5 + 0.45 * Math.sin(time * 2.2));
                double y2 = y + h * (0.5 - 0.45 * Math.sin(time * 2.2));
                ringGlow(d, x, y1, z, r, col(0, A));
                ringGlow(d, x, y2, z, r, col(0.5f, A));
            }
            default -> {
                double yc = y + h * (0.5 + 0.45 * Math.sin(time * 2.2));
                ringGlow(d, x, yc, z, r, col(0, A));
                // «хвост» кольца — светящаяся стенка по ходу движения
                for (int i = 1; i <= 8; i++) {
                    double yy = y + h * (0.5 + 0.45 * Math.sin(time * 2.2 - i * 0.07));
                    d.ring(x, yy, z, r * 0.97, r, 56, 0, col(i / 8f, A * (0.4f - i * 0.045f)));
                }
            }
        }
    }

    private static void ringGlow(WorldDraw d, double x, double y, double z, double r, int c) {
        d.ring(x, y, z, r * 0.9, r, 56, Colors.mulAlpha(c, 0.1f), c);
        d.ring(x, y, z, r, r * 1.12, 56, Colors.mulAlpha(c, 0.35f), Colors.mulAlpha(c, 0f));
    }
}
