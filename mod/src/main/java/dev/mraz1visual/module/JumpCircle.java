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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Мягкое светящееся кольцо под ногами в момент прыжка: тонкая кромка с ореолом расходится по ease-out,
 * следом — вторая, бледнее; в центре на мгновение светится земля. Видишь только ты.
 */
public final class JumpCircle extends Module implements WorldLayer {
    private static final int MAX = 8;

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ring", "Мягкое кольцо", "waves", "Две волны", "disc", "Светящийся диск", "sparks", "Кольцо + искры"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Свой: центр", 0xFFB8E6FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Свой: край", 0xFF7A6BFF));
    final BoolSetting landing = add(new BoolSetting("landing", "Ещё и при приземлении", false));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 1.2, 0.4, 3.0, 0.1, " бл."));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 0.9, 0.3, 2.0, 0.1, " с"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 100, 20, 100, 5, " %"));

    // кольцевой буфер кругов — без аллокаций
    private final double[] cx = new double[MAX], cy = new double[MAX], cz = new double[MAX];
    private final long[] born = new long[MAX];
    private int next;
    private boolean wasOnGround = true;

    public JumpCircle() {
        super("jump_circle", "Jump Circle", "Мягкое светящееся кольцо под ногами при прыжке.", Category.VISUALS);
    }

    private int col(float t, float a) {
        return Fx.color(preset.getId(), t, Fx.time() * 0.3f, color.get(), color2.get(), a * opacity.getF() / 100f);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean onGround = p.onGround();
        boolean jump = wasOnGround && !onGround && p.getDeltaMovement().y > 0.1;
        boolean land = landing.get() && !wasOnGround && onGround;
        if ((jump || land) && style.getId().equals("sparks")) {
            int n = Fx.count(12);
            for (int i = 0; i < n; i++) {
                double a = Math.random() * Math.PI * 2, v = 1.4 + Math.random() * 1.2;
                FxParticles.spawn(p.getX(), p.getY() + 0.05, p.getZ(), Math.cos(a) * v, 0.5 + Math.random() * 0.8, Math.sin(a) * v,
                        0.6f, 0.035f, col(0.1f, 1f), 3.5f, false, FxParticles.SPARK, true, 0.05f, 0f);
            }
        }
        if (jump || land) {
            cx[next] = p.getX();
            cy[next] = p.getY();
            cz[next] = p.getZ();
            born[next] = System.nanoTime();
            next = (next + 1) % MAX;
        }
        wasOnGround = onGround;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.nanoTime();
        double life = duration.get() * 1e9;
        int segs = Fx.segments(64);
        for (int i = 0; i < MAX; i++) {
            if (born[i] == 0) continue;
            float t = (float) ((now - born[i]) / life);
            if (t >= 1) {
                born[i] = 0;
                continue;
            }
            float e = Ease.outExpo(t);
            double r = 0.12 + e * radius.get();
            float a = (float) Math.pow(1 - t, 1.5);
            double y = cy[i] + 0.02;
            switch (style.getId()) {
                case "waves" -> {
                    wave(d, cx[i], y, cz[i], r, a, segs);
                    float t2 = Math.max(0, (t - 0.18f) / 0.82f);
                    if (t2 > 0) wave(d, cx[i], y, cz[i], 0.1 + Ease.outExpo(t2) * radius.get() * 0.8, a * 0.6f, segs);
                }
                case "disc" -> {
                    d.glowBegin();
                    d.ring(cx[i], y, cz[i], 0, r, segs, col(0.2f, a * 0.4f), col(0.6f, a * 0.08f));
                    d.glowEnd();
                    FxDraw.softRing(d, cx[i], y, cz[i], r, 0.012, col(0.15f, a), segs);
                }
                default -> {
                    wave(d, cx[i], y, cz[i], r, a, segs);
                    // земля в центре на мгновение светится
                    if (t < 0.3f) {
                        d.glowBegin();
                        d.ring(cx[i], y, cz[i], 0, 0.45, segs, col(0.1f, Fx.flash(0.35f) * (1 - t / 0.3f)), col(0.4f, 0));
                        d.glowEnd();
                    }
                }
            }
        }
    }

    private void wave(WorldDraw d, double x, double y, double z, double r, float a, int segs) {
        d.glowBegin();
        FxDraw.haloRing(d, x, y, z, r, 0.22, col(0.5f, a * 0.35f), segs);
        d.glowEnd();
        FxDraw.softRing(d, x, y, z, r, 0.014, col(0.1f, a), segs);
    }
}
