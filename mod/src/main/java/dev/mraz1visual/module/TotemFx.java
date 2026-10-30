package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxDraw;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Свой эффект тотема бессмертия: мягкая вспышка, два кольца света поднимаются вдоль тела, золотые искры
 * медленно оседают. Вместо ванильного облака частиц (его можно убрать в No Render → «Анимация тотема»).
 * У других — только если они в прямой видимости.
 */
public final class TotemFx extends Module implements WorldLayer {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS).alias("gold", "sunset"));
    final ColorSetting color = add(new ColorSetting("color", "Свой цвет", 0xFFFFD36B));
    final BoolSetting golden = add(new BoolSetting("golden", "Золото тотема", true).hint("Тёплые золотые тона поверх пресета — как у самого тотема."));
    final BoolSetting others = add(new BoolSetting("others", "У других игроков (в прямой видимости)", true));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));
    final BoolSetting replace = add(new BoolSetting("replace", "Вместо ванильных частиц", true)
            .hint("Убирает облако зелёно-жёлтых частиц тотема. Анимацию тотема на экране прячет No Render."));

    /** Из миксина ParticleEngine: не создавать ванильные частицы тотема. */
    public static boolean replacesVanilla() {
        TotemFx t = instance;
        return t != null && t.isEnabled() && t.replace.get();
    }

    private static final int MAX = 6;
    private final LivingEntity[] who = new LivingEntity[MAX];
    private final long[] born = new long[MAX];
    private int next;
    private static TotemFx instance;

    public TotemFx() {
        super("totem_fx", "Totem FX", "Красивая вспышка, кольца и искры, когда срабатывает тотем.", Category.COMBAT);
        instance = this;
    }

    /** Из миксина LivingEntity.handleEntityEvent(35). */
    public static void onPop(LivingEntity e) {
        TotemFx t = instance;
        if (t == null || !t.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (e != mc.player && (!t.others.get() || !mc.player.hasLineOfSight(e))) return;
        t.who[t.next] = e;
        t.born[t.next] = System.nanoTime();
        t.next = (t.next + 1) % MAX;
        // искры: веером вверх, медленно оседают золотым дождём
        int n = Fx.count(46);
        double h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2, v = 1.2 + Math.random() * 2.2;
            int c = t.col((float) Math.random() * 0.5f, 1f);
            boolean star = i % 5 == 0;
            FxParticles.spawn(e.getX(), e.getY() + h * (0.3 + Math.random() * 0.5), e.getZ(), Math.cos(a) * v, 2 + Math.random() * 2.5, Math.sin(a) * v,
                    1.3f + (float) Math.random() * 0.9f, star ? 0.07f : 0.04f, c, 2.2f, true,
                    star ? FxParticles.STAR : FxParticles.SPARK, true, 0.25f, 0.5f);
        }
    }

    private int col(float t, float a) {
        int c = Fx.color(preset.getId(), t, Fx.time() * 0.4f, color.get(), color.get());
        if (golden.get()) c = Colors.lerp(c, t < 0.3f ? 0xFFFFF6D8 : 0xFFFFC65A, 0.55f);
        return Colors.mulAlpha(c, a);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.nanoTime();
        int segs = Fx.segments(64);
        for (int i = 0; i < MAX; i++) {
            if (who[i] == null) continue;
            float t = (now - born[i]) / 1.4e9f;
            LivingEntity e = who[i];
            if (t >= 1 || e.isRemoved()) { who[i] = null; continue; }
            double x = e.xo + (e.getX() - e.xo) * pt, y = e.yo + (e.getY() - e.yo) * pt, z = e.zo + (e.getZ() - e.zo) * pt;
            double h = e.getBbHeight(), s = size.get();
            d.glowBegin();
            // мягкая вспышка у тела (с «Без вспышек» — едва заметная)
            if (t < 0.35f) {
                float f = Fx.flash(0.55f) * (1 - Ease.smooth(t / 0.35f));
                d.billboardDot(x, y + h * 0.55, z, 1.1 * s, col(0.1f, f), 24);
            }
            // два кольца поднимаются вдоль тела и расширяются
            for (int k = 0; k < 2; k++) {
                float u = Ease.clamp((t - k * 0.15f) / 0.75f);
                if (u <= 0 || u >= 1) continue;
                double r = (0.45 + Ease.outCubic(u) * 0.75) * s;
                double ry = y + h * (0.05 + Ease.inOutSine(u) * 1.05);
                float a = (float) Math.pow(1 - u, 1.3);
                FxDraw.haloRing(d, x, ry, z, r, 0.25 * s, col(0.45f, a * 0.4f), segs);
                d.glowEnd();
                FxDraw.softRing(d, x, ry, z, r, 0.014 * s, col(0.1f, a), segs);
                d.glowBegin();
            }
            // свет, расходящийся по земле
            float g = Ease.clamp(t / 0.9f);
            FxDraw.haloRing(d, x, y + 0.03, z, (0.3 + Ease.outExpo(g) * 1.8) * s, 0.35 * s, col(0.5f, (1 - g) * 0.3f), segs);
            d.glowEnd();
        }
    }
}
