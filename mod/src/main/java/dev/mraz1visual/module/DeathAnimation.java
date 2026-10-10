package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Смерть рассыпается на светящиеся частицы: тело распадается, растворяется вверх,
 * взрывается, улетает душой или осыпается пеплом. Только у тебя на экране, с проверкой глубины.
 */
public final class DeathAnimation extends Module implements WorldLayer {
    private static DeathAnimation instance;
    private static final int MAX_DOTS = 5000;

    final BoolSetting players = add(new BoolSetting("players", "Игроки", true).group("Показывать на"));
    final BoolSetting mobs = add(new BoolSetting("mobs", "Мобы", true).group("Показывать на"));
    final BoolSetting self = add(new BoolSetting("self", "Себе", false).group("Показывать на"));
    final EnumSetting mode = add(new EnumSetting("mode", "Режим", 0,
            "decay", "Распад", "dissolve", "Растворение", "explode", "Взрыв", "soul", "Душа", "ash", "Пепел"));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 2.2, 0.6, 6, 0.1, " с"));
    final SliderSetting density = add(new SliderSetting("density", "Плотность", 1, 0.3, 3, 0.05, "×"));
    final SliderSetting size = add(new SliderSetting("size", "Размер частиц", 1, 0.4, 3, 0.05, "×"));
    final SliderSetting hold = add(new SliderSetting("hold", "Задержка силуэта", 0.15, 0, 0.6, 0.01, ""));
    final SliderSetting power = add(new SliderSetting("power", "Сила разлёта", 1, 0.2, 3, 0.05, "×"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга").group("Вид"));
    final ColorSetting color1 = add(new ColorSetting("color1", "Цвет 1", 0xFFFF5AA8).group("Вид"));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFF7A5CFF).group("Вид"));
    final BoolSetting glow = add(new BoolSetting("glow", "Свечение", true).group("Вид"));
    final BoolSetting hideBody = add(new BoolSetting("hide_body", "Скрывать тело", true).group("Вид"));

    /** Одна частица: точка на теле (относительно ног), направление разлёта, высота 0..1, фаза. */
    private static final class Dot {
        float x, y, z, vx, vy, vz, hf, phase, size;
    }

    private static final class Death {
        final List<Dot> dots = new ArrayList<>();
        double ox, oy, oz;
        float height;
        long born;
        int id;
    }

    private final List<Death> deaths = new ArrayList<>();
    private final Set<Integer> captured = new HashSet<>();
    private final Random rnd = new Random();

    public DeathAnimation() {
        super("death_animation", "Death Animation", "Тело рассыпается на светящиеся частицы при смерти.", Category.VISUALS);
        instance = this;
    }

    @Override
    protected void onDisable() {
        deaths.clear();
        captured.clear();
    }

    /** Из миксина EntityRenderer.shouldRender: прятать тело, пока идёт анимация. */
    public static boolean hides(Entity e) {
        DeathAnimation d = instance;
        return d != null && d.isEnabled() && d.hideBody.get() && e instanceof LivingEntity && d.captured.contains(e.getId());
    }

    private boolean wanted(LivingEntity e, Minecraft mc) {
        if (e instanceof ArmorStand) return false;
        if (e == mc.player) return self.get();
        return e instanceof Player ? players.get() : mobs.get();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) {
            deaths.clear();
            captured.clear();
            return;
        }
        captured.removeIf(id -> {
            boolean anim = false;
            for (Death d : deaths) if (d.id == id) anim = true;
            return !anim;
        });
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !le.isDeadOrDying() || captured.contains(e.getId())) continue;
            if (le.deathTime > 3 || !wanted(le, mc)) continue;
            if (mc.player != null && e != mc.player && e.distanceToSqr(mc.player) > 64 * 64) continue;
            spawn(le);
            captured.add(e.getId());
        }
    }

    // ── форма тела: набор брусков, из поверхности которых берём точки ─────────

    private void spawn(LivingEntity e) {
        Death d = new Death();
        d.ox = e.getX();
        d.oy = e.getY();
        d.oz = e.getZ();
        d.height = Math.max(0.3f, e.getBbHeight());
        d.born = System.currentTimeMillis();
        d.id = e.getId();
        float w = e.getBbWidth(), h = d.height;
        double yaw = Math.toRadians(e.yBodyRot);
        boolean humanoid = e instanceof Player || (h > 1.4f && w < 0.9f);
        if (humanoid) {
            float s = h / 1.8f; // масштаб под рост
            part(d, 0, 1.5f * s, 0, 0.25f * s, 0.25f * s, 0.25f * s, yaw);       // голова
            part(d, 0, 1.05f * s, 0, 0.25f * s, 0.375f * s, 0.125f * s, yaw);    // тело
            part(d, 0.375f * s, 1.05f * s, 0, 0.125f * s, 0.375f * s, 0.125f * s, yaw);  // руки
            part(d, -0.375f * s, 1.05f * s, 0, 0.125f * s, 0.375f * s, 0.125f * s, yaw);
            part(d, 0.125f * s, 0.375f * s, 0, 0.125f * s, 0.375f * s, 0.125f * s, yaw); // ноги
            part(d, -0.125f * s, 0.375f * s, 0, 0.125f * s, 0.375f * s, 0.125f * s, yaw);
        } else {
            part(d, 0, h / 2, 0, w / 2, h / 2, w / 2, yaw);
        }
        // направление разлёта — от центра тела наружу, с разбросом
        float cy = h / 2;
        for (Dot p : d.dots) {
            float ox = p.x, oy = p.y - cy, oz = p.z;
            float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (len < 1e-3f) { ox = rnd.nextFloat() - 0.5f; oy = 0.2f; oz = rnd.nextFloat() - 0.5f; len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz); }
            p.vx = ox / len + (rnd.nextFloat() - 0.5f) * 0.6f;
            p.vy = oy / len + (rnd.nextFloat() - 0.5f) * 0.6f;
            p.vz = oz / len + (rnd.nextFloat() - 0.5f) * 0.6f;
            p.hf = Math.max(0, Math.min(1, p.y / h));
        }
        deaths.add(d);
        int total = 0;
        for (Death x : deaths) total += x.dots.size();
        while (total > MAX_DOTS && deaths.size() > 1) total -= deaths.remove(0).dots.size();
    }

    /** Брусок с центром (cx, cy, cz) в локальных координатах тела и полуразмерами; точки на его гранях. */
    private void part(Death d, float cx, float cy, float cz, float hx, float hy, float hz, double yaw) {
        float ax = hx * 2, ay = hy * 2, az = hz * 2;
        float area = 2f * (ax * ay + ay * az + ax * az);
        int n = Math.max(4, Math.round(area * 95 * density.getF()));
        double c = Math.cos(yaw), s = Math.sin(yaw);
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 2 - 1, b = rnd.nextFloat() * 2 - 1, side = rnd.nextBoolean() ? 1 : -1;
            float r = rnd.nextFloat() * (ax * ay + ay * az + ax * az);
            float lx, ly, lz;
            if (r < ax * ay) { lx = a * hx; ly = b * hy; lz = side * hz; }
            else if (r < ax * ay + ay * az) { lx = side * hx; ly = a * hy; lz = b * hz; }
            else { lx = a * hx; ly = side * hy; lz = b * hz; }
            float px = cx + lx, pz = cz + lz;
            Dot p = new Dot();
            p.x = (float) (px * c - pz * s);
            p.z = (float) (px * s + pz * c);
            p.y = cy + ly;
            p.phase = rnd.nextFloat();
            p.size = 0.7f + rnd.nextFloat() * 0.6f;
            d.dots.add(p);
        }
    }

    // ── отрисовка ─────────────────────────────────────────────

    @Override
    public void renderWorld(WorldDraw w, float pt) {
        if (deaths.isEmpty()) return;
        long now = System.currentTimeMillis();
        float dur = (float) (duration.get() * 1000);
        deaths.removeIf(d -> now - d.born > dur);
        String m = mode.getId();
        float k = power.getF(), holdF = hold.getF();
        float base = 0.03f * size.getF();
        float[] out = new float[3];
        for (Death d : deaths) {
            float t = Math.min(1f, (now - d.born) / dur);
            float totalSec = dur / 1000f;
            for (Dot p : d.dots) {
                float start = m.equals("dissolve") ? holdF + p.hf * 0.45f + p.phase * 0.08f : holdF * (0.6f + p.phase * 0.4f);
                float lt = Math.max(0f, (t - start) / Math.max(0.05f, 1f - start));
                float sec = lt * totalSec * (1f - start);
                position(m, p, d, sec, lt, k, out);
                float alpha = t < start ? 1f : (float) Math.pow(1f - lt, 1.3);
                if (alpha <= 0.01f) continue;
                float twinkle = 0.75f + 0.25f * (float) Math.sin(now * 0.012 + p.phase * 40);
                int c = color(p, t, d.id);
                double sz = base * p.size * (m.equals("ash") ? 1f : 1f + lt * 0.4f);
                double x = d.ox + out[0], y = d.oy + out[1], z = d.oz + out[2];
                if (glow.get()) w.billboardDot(x, y, z, sz * 3.2, Colors.mulAlpha(c, alpha * 0.22f * twinkle), 6);
                w.billboardDot(x, y, z, sz, Colors.mulAlpha(Colors.lerp(c, 0xFFFFFFFF, 0.35f), alpha * twinkle), 5);
            }
        }
    }

    private int color(Dot p, float t, int id) {
        String cm = colorMode.getId();
        if (cm.equals("gradient")) return Colors.lerp(color1.get() | 0xFF000000, color2.get() | 0xFF000000, Math.min(1f, p.hf * 0.8f + p.phase * 0.2f + t * 0.3f));
        if (cm.equals("rainbow")) return 0xFF000000 | Colors.hsv((p.hf * 0.5f + id * 0.13f + now() * 0.2f) % 1f, 0.6f, 1f);
        return ColorFx.pick("solid", color1.get(), color2.get(), 0) | 0xFF000000;
    }

    private static float now() {
        return (System.currentTimeMillis() % 100000) / 1000f;
    }

    /** Где частица сейчас (относительно ног), по режиму. */
    private static void position(String m, Dot p, Death d, float sec, float lt, float k, float[] out) {
        switch (m) {
            case "dissolve" -> {
                float drift = sec * sec * 0.9f;
                float wob = (float) Math.sin(sec * 3f + p.phase * 12f) * 0.25f * lt;
                out[0] = p.x + p.vx * sec * 0.25f * k + wob;
                out[1] = p.y + drift * k + sec * 0.4f;
                out[2] = p.z + p.vz * sec * 0.25f * k + wob * 0.6f;
            }
            case "explode" -> {
                float speed = 4.5f * k;
                out[0] = p.x + p.vx * speed * sec;
                out[2] = p.z + p.vz * speed * sec;
                out[1] = Math.max(0.02f, p.y + (p.vy * speed + 2.5f) * sec - 4.9f * sec * sec);
            }
            case "soul" -> {
                float e = 1f - (1f - lt) * (1f - lt);
                float ang = e * 6.5f + p.phase * 6.283f;
                float r = (0.15f + 0.35f * (1f - p.hf)) * (1f - e * 0.6f) * k;
                out[0] = p.x * (1f - e) + (float) Math.cos(ang) * r * e;
                out[1] = p.y + e * 2.8f * k + p.hf * e;
                out[2] = p.z * (1f - e) + (float) Math.sin(ang) * r * e;
            }
            case "ash" -> {
                float fall = Math.min(p.y, 4.9f * sec * sec * (0.4f + p.phase * 0.6f));
                float spread = Math.min(1f, fall / Math.max(0.1f, d.height)) * 0.5f;
                out[0] = p.x + p.vx * spread;
                out[1] = Math.max(0.02f, p.y - fall);
                out[2] = p.z + p.vz * spread;
            }
            default -> {
                float travel = (1f - (float) Math.exp(-sec * 1.6f)) * 1.4f * k;
                float swirl = (float) Math.sin(sec * 2.5f + p.phase * 9f) * 0.12f * lt;
                out[0] = p.x + p.vx * travel + swirl;
                out[1] = p.y + p.vy * travel * 0.6f + sec * 0.35f;
                out[2] = p.z + p.vz * travel - swirl;
            }
        }
    }
}
