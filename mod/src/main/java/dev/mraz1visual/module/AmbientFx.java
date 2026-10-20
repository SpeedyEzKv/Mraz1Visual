package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Random;

/** Атмосфера вокруг тебя: светлячки, парящие кубики или светящиеся руны. Чистая косметика. */
public final class AmbientFx extends Module implements WorldLayer {
    private static final int MAX = 80;
    final EnumSetting mode = add(new EnumSetting("mode", "Вид", 0, "fireflies", "Светлячки", "cubes", "Кубики", "glyphs", "Руны"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFF6E27A));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFFB8B4F2));
    final SliderSetting count = add(new SliderSetting("count", "Количество", 30, 5, MAX, 1, ""));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 7, 3, 16, 0.5, " бл."));

    private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX], vx = new double[MAX], vy = new double[MAX], vz = new double[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], seed = new float[MAX];
    private final Random rnd = new Random();
    private long last;

    public AmbientFx() {
        super("ambient_fx", "Ambient FX", "Светлячки, кубики или руны вокруг тебя.", Category.SKY);
    }

    private void respawn(int i, LocalPlayer p) {
        double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * radius.get();
        x[i] = p.getX() + Math.cos(a) * r;
        z[i] = p.getZ() + Math.sin(a) * r;
        y[i] = p.getY() + rnd.nextDouble() * 3;
        vx[i] = (rnd.nextDouble() - 0.5) * 0.3;
        vy[i] = mode.getId().equals("fireflies") ? (rnd.nextDouble() - 0.5) * 0.2 : 0.12 + rnd.nextDouble() * 0.2;
        vz[i] = (rnd.nextDouble() - 0.5) * 0.3;
        age[i] = 0;
        life[i] = 4 + rnd.nextFloat() * 5;
        seed[i] = rnd.nextFloat() * 100;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        int n = count.getI();
        String m = mode.getId();
        double r2 = Math.pow(radius.get() + 3, 2);
        float t = now / 1e9f;
        for (int i = 0; i < n; i++) {
            if (life[i] == 0 || age[i] >= life[i] || Math.pow(x[i] - p.getX(), 2) + Math.pow(z[i] - p.getZ(), 2) > r2) respawn(i, p);
            age[i] += dt;
            // плавное блуждание
            vx[i] += Math.sin(t * 0.7 + seed[i]) * 0.02 * dt * 10;
            vz[i] += Math.cos(t * 0.6 + seed[i]) * 0.02 * dt * 10;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            z[i] += vz[i] * dt;
            float k = age[i] / life[i];
            float a = k < 0.15f ? k / 0.15f : k > 0.8f ? (1 - k) / 0.2f : 1f;
            int c = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), seed[i] % 1f) | 0xFF000000;
            switch (m) {
                case "cubes" -> d.cube(x[i], y[i], z[i], 0.08, t * 0.8 + seed[i], t * 0.6 + seed[i], Colors.mulAlpha(c, a * 0.35f), Colors.mulAlpha(c, a * 0.9f));
                case "glyphs" -> glyph(d, i, Colors.mulAlpha(c, a * 0.9f), t);
                default -> {
                    float blink = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * 1.7 + seed[i]));
                    d.billboardDot(x[i], y[i], z[i], 0.16, Colors.mulAlpha(c, a * blink * 0.35f), 8);
                    d.billboardDot(x[i], y[i], z[i], 0.045, Colors.mulAlpha(0xFFFFFFFF, a * blink), 6);
                }
            }
        }
    }

    /** Руна: три-четыре коротких штриха, медленно вращается. */
    private void glyph(WorldDraw d, int i, int c, float t) {
        Random g = new Random((long) (seed[i] * 1000));
        double rot = t * 0.5 + seed[i];
        double cr = Math.cos(rot), sr = Math.sin(rot), s = 0.14;
        double px = g.nextDouble() - 0.5, py = g.nextDouble() - 0.5;
        for (int k = 0; k < 4; k++) {
            double qx = g.nextDouble() - 0.5, qy = g.nextDouble() - 0.5;
            d.line(x[i] + px * s * cr, y[i] + py * s, z[i] + px * s * sr, x[i] + qx * s * cr, y[i] + qy * s, z[i] + qx * s * sr, 0.018, c);
            px = qx;
            py = qy;
        }
        d.billboardDot(x[i], y[i], z[i], 0.14, Colors.mulAlpha(c, 0.15f), 6);
    }
}
