package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxDraw;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.Ribbon;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Маркер цели: изящный эффект на сущности, которую ты бьёшь. Только пока цель в прямой видимости —
 * за стеной маркер плавно гаснет (никакой информации сквозь блоки). При смене цели маркер «перетекает»
 * к новой, при каждом ударе — короткий пульс. Цвета — общая палитра.
 */
public final class TargetEsp extends Module implements WorldLayer, AttackListener {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0,
            "ring", "Кольцо у ног", "orbit", "Орбита", "diamond", "Стеклянный ромб", "soul", "Душа",
            "ghosts", "Призраки", "marker", "Уголки", "crystals", "Кристаллы", "spiral", "Спираль", "glow", "Сияющий круг")
            .alias("base", "ring").alias("double", "ring").alias("lightning", "soul"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Свой: центр", 0xFFB8E6FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Свой: край", 0xFF7A6BFF));
    final SliderSetting keep = add(new SliderSetting("keep", "Держать после удара", 3, 1, 10, 0.5, " с"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.3, 3, 0.1, "×"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 100, 20, 100, 5, " %"));
    final BoolSetting pulse = add(new BoolSetting("pulse", "Пульс при ударе", true));

    private LivingEntity target;
    private long hitAt;
    private float fade, hitPulse;
    private long lastFrame;
    private double sx, sy, sz, sh = 1.8, sr = 0.5;
    private final Ribbon[] trails = {new Ribbon(48), new Ribbon(48), new Ribbon(48)};

    public TargetEsp() {
        super("target_esp", "Target Marker", "Изящный маркер на цели, которую ты бьёшь (только пока её видно).", Category.VISUALS);
        for (Ribbon r : trails) { r.life = 0.35f; r.minStep = 0.03; }
    }

    /** Цель маркера, если она сейчас видна (для пульса из Hit FX). */
    public static void pulse() {
        TargetEsp t = instance;
        if (t != null && t.pulse.get()) t.hitPulse = 1f;
    }

    private static TargetEsp instance;

    {
        instance = this;
    }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            if (target == null || fade < 0.05f) {
                sx = le.getX();
                sy = le.getY();
                sz = le.getZ();
                sh = le.getBbHeight();
                sr = le.getBbWidth();
                for (Ribbon r : trails) r.clear();
            }
            target = le;
            hitAt = System.currentTimeMillis();
            if (pulse.get()) hitPulse = 1f;
        }
    }

    private int col(float t, float a) {
        return Fx.color(preset.getId(), t, Fx.time() * 0.4f, color.get(), color2.get(), a * opacity.getF() / 100f);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long nowNs = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        LivingEntity t = target;
        if (t == null) return;
        Minecraft mc = Minecraft.getInstance();
        // честно: маркер только у цели в прямой видимости (за блоками — гаснет)
        boolean visible = mc.player != null && mc.player.hasLineOfSight(t);
        boolean alive = !t.isRemoved() && !t.isDeadOrDying() && System.currentTimeMillis() - hitAt < keep.get() * 1000 && visible;
        fade = Ease.approach(fade, alive ? 1f : 0f, alive ? 9f : 6f, dt);
        hitPulse = Ease.approach(hitPulse, 0f, 5f, dt);
        if (fade <= 0.01f && !alive) {
            target = null;
            fade = 0;
            return;
        }
        // позиция с интерполяцией + мягкое «перетекание» (смена цели, телепорт цели)
        double tx = t.xo + (t.getX() - t.xo) * pt, ty = t.yo + (t.getY() - t.yo) * pt, tz = t.zo + (t.getZ() - t.zo) * pt;
        sx = Ease.approach(sx, tx, 22, dt);
        sy = Ease.approach(sy, ty, 22, dt);
        sz = Ease.approach(sz, tz, 22, dt);
        sh = Ease.approach(sh, t.getBbHeight(), 10, dt);
        sr = Ease.approach(sr, t.getBbWidth(), 10, dt);
        double x = sx, y = sy, z = sz, h = sh;
        float time = Fx.time() * speed.getF();
        float p = Ease.smooth(hitPulse);
        double r = (sr * 0.75 + 0.3) * size.get() * (1 + 0.12 * p);
        float A = Ease.smooth(fade);
        switch (style.getId()) {
            case "orbit" -> orbit(d, x, y, z, r, h, time, A, dt, pt);
            case "diamond" -> diamond(d, x, y, z, h, time, A, p);
            case "soul" -> soul(d, x, y, z, r, h, time, A);
            case "ghosts" -> ghosts(d, x, y, z, r, h, time, A);
            case "marker" -> {
                double cy = y + h * 0.55;
                double s = Math.max(h, r * 2) * 0.42 * (1 + 0.04 * Math.sin(time * 3) + 0.1 * p);
                d.billboardCorners(x, cy, z, s, time * 0.8, 0.035, col(0.2f, A));
                d.glowBegin();
                d.billboardCorners(x, cy, z, s * 1.04, time * 0.8, 0.11, col(0.5f, A * 0.25f));
                d.glowEnd();
            }
            case "crystals" -> {
                for (int i = 0; i < 3; i++) {
                    double a = time * 1.1 + i * Math.PI * 2 / 3;
                    double yy = y + h * (0.55 + 0.12 * Math.sin(time * 1.7 + i * 2));
                    double cx = x + Math.cos(a) * r * 1.2, cz = z + Math.sin(a) * r * 1.2;
                    FxDraw.diamond(d, cx, yy, cz, 0.07, 0.13, time * 2 + i, col(0.4f, A * 0.55f), col(0.1f, A));
                    d.glowBegin();
                    d.billboardDot(cx, yy, cz, 0.25, col(0.5f, A * 0.22f), 12);
                    d.glowEnd();
                }
            }
            case "spiral" -> {
                int n = Fx.count(36);
                for (int i = 0; i < n; i++) {
                    float f = i / (float) n;
                    double a = time * 2.2 + f * Math.PI * 4;
                    double yy = y + h * ((f + time * 0.25) % 1.0);
                    d.billboardDot(x + Math.cos(a) * r, yy, z + Math.sin(a) * r, 0.05, col(f, A * (0.3f + 0.7f * f)), 8);
                }
            }
            case "glow" -> {
                double rr = r * 1.3 * (1 + 0.03 * Math.sin(time * 2.4));
                d.glowBegin();
                FxDraw.haloRing(d, x, y + 0.03, z, rr, 0.35, col(0.5f, A * 0.45f), Fx.segments(64));
                d.glowEnd();
                FxDraw.softRing(d, x, y + 0.035, z, rr, 0.02, col(0.1f, A), Fx.segments(64));
            }
            default -> ring(d, x, y, z, r, time, A, p);
        }
    }

    /** Кольцо у ног: тонкая кромка, мягкий ореол, медленно вращающиеся штрихи и второе бледное кольцо. */
    private void ring(WorldDraw d, double x, double y, double z, double r, float time, float A, float p) {
        int segs = Fx.segments(72);
        double yy = y + 0.025;
        d.glowBegin();
        FxDraw.haloRing(d, x, yy, z, r, 0.28, col(0.5f, A * (0.32f + 0.4f * p)), segs);
        d.glowEnd();
        FxDraw.softRing(d, x, yy, z, r, 0.018, col(0.1f, A), segs);
        FxDraw.dashes(d, x, yy + 0.002, z, r * 1.16, 0.035, 6, 0.42f, time * 0.9, col(0.35f, A * 0.8f));
        FxDraw.softRing(d, x, yy, z, r * 0.62, 0.008, col(0.6f, A * 0.35f), segs);
    }

    /** Орбита: три огонька по наклонным орбитам вокруг тела, за каждым — плавный шлейф-лента. */
    private void orbit(WorldDraw d, double x, double y, double z, double r, double h, float time, float A, float dt, float pt) {
        for (int g = 0; g < 3; g++) {
            double a = time * 2.0 + g * Math.PI * 2 / 3;
            double tilt = 0.35 * Math.sin(a * 0.5 + g);
            double ox = x + Math.cos(a) * r * 1.1, oz = z + Math.sin(a) * r * 1.1;
            double oy = y + h * (0.55 + 0.22 * Math.sin(a + g * 1.7)) + tilt * 0.2;
            Ribbon rb = trails[g];
            rb.push(ox, oy, oz, dt);
            d.glowBegin();
            rb.draw(d, Ribbon.FACING, 0.09, A * 0.8f, preset.getId(), color.get(), color2.get(), ox, oy, oz);
            d.billboardDot(ox, oy, oz, 0.2, col(0.3f, A * 0.35f), 12);
            d.glowEnd();
            d.billboardDot(ox, oy, oz, 0.045, Colors.mulAlpha(0xFFFFFFFF, A * opacity.getF() / 100f), 8);
        }
    }

    /** Стеклянный ромб над головой: медленно вращается и покачивается, грани ловят «свет». */
    private void diamond(WorldDraw d, double x, double y, double z, double h, float time, float A, float p) {
        double cy = y + h + 0.42 + 0.05 * Math.sin(time * 1.8);
        double s = 1 + 0.15 * p;
        d.glowBegin();
        d.billboardDot(x, cy, z, 0.32 * s, col(0.45f, A * 0.25f), 16);
        d.glowEnd();
        FxDraw.diamond(d, x, cy, z, 0.1 * s, 0.17 * s, time * 0.9, col(0.45f, A * 0.42f), col(0.08f, A));
    }

    /** Душа: две призрачные ленты спиралью обвивают цель снизу вверх. */
    private void soul(WorldDraw d, double x, double y, double z, double r, double h, float time, float A) {
        int n = Fx.count(40);
        d.glowBegin();
        for (int s = 0; s < 2; s++) {
            double lx = 0, ly = 0, lz = 0;
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                double a = time * 1.6 + s * Math.PI + f * Math.PI * 3.2;
                double rr = r * (1.05 - 0.25 * f);
                double px = x + Math.cos(a) * rr, pz = z + Math.sin(a) * rr, py = y + h * (0.05 + 0.95 * f);
                if (i > 0) {
                    // ярче в середине ленты, концы растворяются
                    float k = (float) Math.sin(Math.PI * f);
                    d.line(lx, ly, lz, px, py, pz, 0.12 * k + 0.02, col(0.3f + 0.5f * f, A * 0.35f * k));
                    d.line(lx, ly, lz, px, py, pz, 0.03 * k + 0.008, col(0.08f, A * 0.9f * k));
                }
                lx = px; ly = py; lz = pz;
            }
        }
        d.glowEnd();
    }

    /** Призраки: три огонька с хвостом из гаснущих точек. */
    private void ghosts(WorldDraw d, double x, double y, double z, double r, double h, float time, float A) {
        int tail = Fx.count(12);
        for (int g = 0; g < 3; g++) {
            for (int i = 0; i < tail; i++) {
                float tt = time * 2.2f - i * 0.05f;
                double a = tt + g * Math.PI * 2 / 3;
                double yy = y + h * (0.5 + 0.3 * Math.sin(tt * 1.3 + g * 2.1));
                float fa = 1f - i / (float) tail;
                d.billboardDot(x + Math.cos(a) * r * 1.05, yy, z + Math.sin(a) * r * 1.05, 0.13 * fa + 0.025, col(0.2f + i * 0.04f, A * fa * 0.75f), 8);
            }
        }
    }
}
