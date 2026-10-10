package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.core.BlockPos;

/** Эффект на месте цели, которую ты добил: молния, души или ударная волна. Только у тебя. */
public final class KillEffect extends Module implements AttackListener, WorldLayer {
    final EnumSetting style = add(new EnumSetting("style", "Эффект", 0, "blood", "Кровь", "lightning", "Молния", "souls", "Души", "wave", "Волна", "shards", "Осколки", "all", "Всё сразу"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFB8B4F2));
    final BoolSetting sound = add(new BoolSetting("sound", "Звук", true));
    final BoolSetting playersOnly = add(new BoolSetting("players_only", "Только игроки", false));
    final BoolSetting allDeaths = add(new BoolSetting("all_deaths", "На всех смертях рядом, не только своих", false));
    final ColorSetting bloodColor = add(new ColorSetting("blood_color", "Цвет крови", 0xFF8A0303).group("Кровь"));
    final SliderSetting bloodAmount = add(new SliderSetting("blood_amount", "Количество", 70, 10, 250, 5, "").group("Кровь"));
    final SliderSetting bloodPower = add(new SliderSetting("blood_power", "Сила разлёта", 1, 0.3, 2.5, 0.1, "×").group("Кровь"));
    final SliderSetting bloodSize = add(new SliderSetting("blood_size", "Размер", 1, 0.3, 3, 0.1, "×").group("Кровь"));
    final SliderSetting bloodTime = add(new SliderSetting("blood_time", "Пятна держатся", 6, 1, 30, 0.5, " с").group("Кровь"));

    /** Капля в полёте. */
    private static final class Drop {
        double x, y, z, vx, vy, vz, ground;
        float size;
    }

    /** Пятно на земле: неровная клякса, растёт, темнеет (сохнет) и тает. */
    private static final class Splat {
        double x, y, z;
        float radius;
        long start;
        float[] jag;
    }

    private final List<Drop> drops = new ArrayList<>();
    private final List<Splat> splats = new ArrayList<>();
    private long lastFrame;

    private static final class Fx {
        double x, y, z, h;
        long start;
        double[] bolt;   // точки молнии
        double[] souls;  // смещения душ
    }

    private final List<Fx> fx = new ArrayList<>();
    private final Random rnd = new Random();
    private LivingEntity last;
    private long lastAt;

    public KillEffect() {
        super("kill_effect", "Kill Effect", "Кровь, молния, души или волна, когда ты добиваешь цель.", Category.VISUALS);
        instance = this;
    }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            if (playersOnly.get() && !(le instanceof net.minecraft.world.entity.player.Player)) return;
            last = le;
            lastAt = System.currentTimeMillis();
        }
    }

    private static KillEffect instance;
    private final List<LivingEntity> pending = new ArrayList<>();
    private final java.util.Set<Integer> done = new java.util.HashSet<>();

    /** Из миксина LivingEntity.handleEntityEvent(3): кто-то рядом умер. */
    public static void onDeath(LivingEntity e) {
        KillEffect k = instance;
        if (k == null || !k.isEnabled() || !k.allDeaths.get() || e == Minecraft.getInstance().player) return;
        if (k.playersOnly.get() && !(e instanceof net.minecraft.world.entity.player.Player)) return;
        if (k.pending.size() < 16) k.pending.add(e);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (done.size() > 256) done.clear();
        while (!pending.isEmpty()) {
            LivingEntity e = pending.remove(0);
            if (done.add(e.getId())) play(mc, e);
        }
        if (last == null) return;
        if (System.currentTimeMillis() - lastAt > 3000) {
            last = null;
            return;
        }
        if (!last.isDeadOrDying()) return;
        if (done.add(last.getId())) play(mc, last);
        last = null;
    }

    private void play(Minecraft mc, LivingEntity last) {
        Fx f = new Fx();
        f.x = last.getX();
        f.y = last.getY();
        f.z = last.getZ();
        f.h = last.getBbHeight();
        f.start = System.currentTimeMillis();
        // молния: ломаная сверху вниз
        int n = 10;
        f.bolt = new double[(n + 1) * 3];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double j = i == 0 || i == n ? 0 : 0.55;
            f.bolt[i * 3] = f.x + (rnd.nextDouble() - 0.5) * j;
            f.bolt[i * 3 + 1] = f.y + 14 * (1 - t);
            f.bolt[i * 3 + 2] = f.z + (rnd.nextDouble() - 0.5) * j;
        }
        f.souls = new double[18];
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 0.2 + rnd.nextDouble() * 0.4;
            f.souls[i * 3] = Math.cos(a) * r;
            f.souls[i * 3 + 1] = rnd.nextDouble() * 0.6;
            f.souls[i * 3 + 2] = Math.sin(a) * r;
        }
        fx.add(f);
        if (style.getId().equals("blood")) blood(mc, f);
        if (style.getId().equals("shards") || style.getId().equals("all")) {
            for (int i = 0; i < 40; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, v = 1.5 + rnd.nextDouble() * 3;
                dev.mraz1visual.render.FxParticles.spawn(f.x, f.y + f.h * (0.2 + rnd.nextDouble() * 0.7), f.z,
                        Math.cos(a) * v, 1 + rnd.nextDouble() * 3, Math.sin(a) * v, 1.1f + rnd.nextFloat() * 0.5f,
                        0.05f + rnd.nextFloat() * 0.06f, i % 3 == 0 ? 0xFFFFFFFF : color.get() | 0xFF000000, 9f, i % 3 != 0);
            }
        }
        if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1f));
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.currentTimeMillis();
        renderBlood(d, now);
        fx.removeIf(f -> now - f.start > 1600);
        String s = style.getId();
        int c = color.get();
        for (Fx f : fx) {
            float t = (now - f.start) / 1600f;
            if (s.equals("lightning") || s.equals("all")) {
                float a = t < 0.35f ? 1f - t / 0.35f * 0.5f : Math.max(0, 0.5f - (t - 0.35f));
                if (a > 0) {
                    for (int i = 0; i + 3 < f.bolt.length; i += 3) {
                        d.line(f.bolt[i], f.bolt[i + 1], f.bolt[i + 2], f.bolt[i + 3], f.bolt[i + 4], f.bolt[i + 5], 0.35, Colors.mulAlpha(c, a * 0.25f));
                        d.line(f.bolt[i], f.bolt[i + 1], f.bolt[i + 2], f.bolt[i + 3], f.bolt[i + 4], f.bolt[i + 5], 0.07, Colors.mulAlpha(0xFFFFFFFF, a));
                    }
                }
            }
            if (s.equals("souls") || s.equals("all")) {
                float a = 1f - t;
                for (int i = 0; i < f.souls.length; i += 3) {
                    double rise = t * 2.2 + f.souls[i + 1];
                    double sw = Math.sin((t * 6 + i) ) * 0.15;
                    d.billboardDot(f.x + f.souls[i] + sw, f.y + f.h * 0.5 + rise, f.z + f.souls[i + 2], 0.18, Colors.mulAlpha(c, a * 0.9f));
                    d.billboardDot(f.x + f.souls[i] + sw, f.y + f.h * 0.5 + rise, f.z + f.souls[i + 2], 0.07, Colors.mulAlpha(0xFFFFFFFF, a));
                }
            }
            if (s.equals("wave") || s.equals("all")) {
                double r = 0.3 + t * 3.5;
                float a = 1f - t;
                d.ring(f.x, f.y + 0.05, f.z, r - 0.12, r, 48, Colors.mulAlpha(c, a * 0.2f), Colors.mulAlpha(c, a));
                d.ring(f.x, f.y + 0.05, f.z, r * 0.6 - 0.06, r * 0.6, 40, Colors.mulAlpha(c, 0), Colors.mulAlpha(c, a * 0.6f));
            }
        }
    }

    // ── кровь ────────────────────────────────────────────────

    private void blood(Minecraft mc, Fx f) {
        int n = bloodAmount.getI();
        double pw = bloodPower.get();
        float sc = bloodSize.getF();
        double ground = groundY(mc, f.x, f.y + 0.5, f.z);
        for (int i = 0; i < n; i++) {
            Drop dr = new Drop();
            dr.x = f.x + (rnd.nextDouble() - 0.5) * 0.5;
            dr.y = f.y + f.h * (0.3 + rnd.nextDouble() * 0.6);
            dr.z = f.z + (rnd.nextDouble() - 0.5) * 0.5;
            double a = rnd.nextDouble() * Math.PI * 2, v = (0.8 + rnd.nextDouble() * 2.6) * pw;
            dr.vx = Math.cos(a) * v;
            dr.vz = Math.sin(a) * v;
            dr.vy = (1.0 + rnd.nextDouble() * 3.2) * pw;
            dr.size = (float) (0.025 + Math.pow(rnd.nextDouble(), 2.2) * 0.075) * sc;
            dr.ground = ground;
            drops.add(dr);
        }
        while (drops.size() > 900) drops.remove(0);
    }

    /** Верх первого твёрдого блока под точкой (не глубже 6 блоков). */
    private static double groundY(Minecraft mc, double x, double y, double z) {
        if (mc.level == null) return y - 1;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y); by > y - 6; by--) {
            if (!mc.level.getBlockState(new BlockPos(bx, by, bz)).isAir()) return by + 1;
        }
        return y - 6;
    }

    private void renderBlood(WorldDraw d, long now) {
        float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int base = bloodColor.get() | 0xFF000000;
        // капли: летят, падают, на земле превращаются в пятна
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop dr = drops.get(i);
            dr.vy -= 18 * dt;
            dr.vx *= Math.pow(0.6, dt);
            dr.vz *= Math.pow(0.6, dt);
            dr.x += dr.vx * dt;
            dr.y += dr.vy * dt;
            dr.z += dr.vz * dt;
            if (dr.y <= dr.ground) {
                Splat s = new Splat();
                s.x = dr.x;
                s.y = dr.ground + 0.01 + splats.size() % 40 * 0.0004; // чтобы пятна не мерцали друг в друге
                s.z = dr.z;
                double sp = Math.min(0.8, Math.hypot(dr.vx, dr.vz) / 6);
                s.radius = (float) (dr.size * (2.2 + sp * 4));
                s.start = now;
                s.jag = new float[14];
                for (int k = 0; k < s.jag.length; k++) s.jag[k] = 0.7f + rnd.nextFloat() * 0.5f;
                splats.add(s);
                while (splats.size() > 500) splats.remove(0);
                drops.remove(i);
                continue;
            }
            // капля вытянута по скорости
            double l = 0.035;
            d.line(dr.x, dr.y, dr.z, dr.x - dr.vx * l, dr.y - dr.vy * l, dr.z - dr.vz * l, dr.size, base);
            d.billboardDot(dr.x, dr.y, dr.z, dr.size * 0.6, base, 6);
        }
        long life = (long) (bloodTime.get() * 1000);
        splats.removeIf(s -> now - s.start > life);
        int dry = Colors.lerp(base, 0xFF2A0505, 1f);
        for (Splat s : splats) {
            float age = (now - s.start) / (float) life;
            float grow = 1f - (float) Math.pow(1f - Math.min(1f, (now - s.start) / 260f), 3);
            float fade = 1f - smooth((age - 0.7f) / 0.3f);
            int c = Colors.mulAlpha(Colors.lerp(base, dry, smooth(age / 0.8f)), 0.92f * fade);
            int edge = Colors.mulAlpha(c, 0.75f);
            float r = s.radius * (0.35f + 0.65f * grow);
            int n = s.jag.length;
            for (int k = 0; k < n; k++) {
                double a0 = k * Math.PI * 2 / n, a1 = (k + 1) * Math.PI * 2 / n;
                float r0 = r * s.jag[k], r1 = r * s.jag[(k + 1) % n];
                d.quad(s.x, s.y, s.z, s.x + Math.cos(a0) * r0, s.y, s.z + Math.sin(a0) * r0,
                        s.x + Math.cos(a1) * r1, s.y, s.z + Math.sin(a1) * r1, s.x, s.y, s.z, c, edge, edge, c);
            }
        }
    }

    private static float smooth(float v) {
        float t = Math.max(0f, Math.min(1f, v));
        return t * t * (3 - 2 * t);
    }
}
