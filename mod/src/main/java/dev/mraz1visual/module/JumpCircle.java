package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Расходящийся круг на земле в точке прыжка. Видишь только ты. */
public final class JumpCircle extends Module implements WorldLayer {
    private static final int MAX = 8;

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ring", "Кольцо", "waves", "Две волны", "disc", "Светящийся диск", "sparks", "Кольцо + искры"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xD0B8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xD07FD8FF));
    final BoolSetting landing = add(new BoolSetting("landing", "Ещё и при приземлении", false));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 1.2, 0.4, 3.0, 0.1, " бл."));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 0.8, 0.3, 2.0, 0.1, " с"));

    // кольцевой буфер кругов — без аллокаций
    private final double[] cx = new double[MAX], cy = new double[MAX], cz = new double[MAX];
    private final long[] born = new long[MAX];
    private int next;
    private boolean wasOnGround = true;

    public JumpCircle() {
        super("jump_circle", "Jump Circle", "Круг на земле, когда прыгаешь.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean onGround = p.onGround();
        boolean jump = wasOnGround && !onGround && p.getDeltaMovement().y > 0.1;
        boolean land = landing.get() && !wasOnGround && onGround;
        if ((jump || land) && style.getId().equals("sparks")) {
            for (int i = 0; i < 14; i++) {
                double a = Math.random() * Math.PI * 2, v = 1.2 + Math.random() * 1.2;
                int c = dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), color.get(), color2.get(), (float) (a / (Math.PI * 2)));
                dev.mraz1visual.render.FxParticles.spawn(p.getX(), p.getY() + 0.05, p.getZ(), Math.cos(a) * v, 0.6 + Math.random(), Math.sin(a) * v,
                        0.7f, 0.07f, c | 0xFF000000, 3.5f, true);
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
        int base = color.get();
        for (int i = 0; i < MAX; i++) {
            if (born[i] == 0) continue;
            double t = (now - born[i]) / life;
            if (t >= 1) {
                born[i] = 0;
                continue;
            }
            double e = 1 - Math.pow(1 - t, 3); // ease-out
            double r = 0.15 + e * radius.get();
            float a = (float) (1 - t);
            int c1 = Colors.mulAlpha(dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), base, color2.get(), 0f), a);
            int c2 = Colors.mulAlpha(dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), base, color2.get(), 0.5f), a);
            double y = cy[i] + 0.02;
            switch (style.getId()) {
                case "waves" -> {
                    d.ring(cx[i], y, cz[i], r * 0.85, r, 56, Colors.mulAlpha(c1, 0.1f), c1);
                    double r2 = 0.1 + Math.max(0, e - 0.25) * radius.get();
                    d.ring(cx[i], y, cz[i], r2 * 0.8, r2, 56, Colors.mulAlpha(c2, 0.1f), c2);
                }
                case "disc" -> {
                    d.ring(cx[i], y, cz[i], 0, r, 56, Colors.mulAlpha(c1, 0.05f), Colors.mulAlpha(c1, 0.55f));
                    d.ring(cx[i], y, cz[i], r, r * 1.08, 56, c2, Colors.mulAlpha(c2, 0f));
                }
                default -> {
                    d.ring(cx[i], y, cz[i], r * 0.8, r, 56, Colors.mulAlpha(c1, 0.15f), c1);
                    d.ring(cx[i], y, cz[i], r, r * 1.12, 56, Colors.mulAlpha(c2, 0.5f), Colors.mulAlpha(c2, 0f));
                }
            }
        }
    }
}
