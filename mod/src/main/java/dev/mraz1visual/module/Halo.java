package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Ореол над головой — у самой модели (повторяет наклон головы; виден от 3-го лица, в инвентаре и превью).
 * Нимб: тонкое светящееся кольцо с мягким свечением и бегущим по кругу бликом, медленно покачивается.
 * Корона из света: кольцо и тонкие лучи-зубцы с сияющими кончиками. Орбита: огоньки на наклонной орбите со следами.
 */
public final class Halo extends Module {
    private static Halo instance;

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ring", "Нимб", "crown", "Корона из света", "orbit", "Орбитальные огоньки")
            .alias("double", "ring"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 2, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Центр (свой)", 0xFFFFF4C8));
    final ColorSetting color2 = add(new ColorSetting("color2", "Край (свой)", 0xFFF6C66B));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.6, 1.6, 0.05, "×"));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.18, 0.05, 0.45, 0.01, " бл."));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.2, 3, 0.05, "×"));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 85, 20, 100, 5, " %"));

    private final WorldDraw.Painter painter = this::paint;
    private static final long START = System.nanoTime();
    private float time;

    public Halo() {
        super("halo", "Halo", "Нимб, корона из света или орбитальные огоньки над головой.", Category.COSMETICS);
        instance = this;
    }

    public static boolean active() {
        Halo h = instance;
        return h != null && h.isEnabled();
    }

    /** Из слоя модели игрока; поза — у головы (оси: вправо +X, вверх +Y, вперёд −Z, начало — шея, единица — блок). */
    public static void submit(PoseStack ps, SubmitNodeCollector c, boolean gui) {
        Halo h = instance;
        if (h == null || !h.isEnabled()) return;
        h.time = (System.nanoTime() - START) / 1e9f * h.speed.getF();
        WorldDraw.submitLocal(ps, c, gui, h.painter);
    }

    private int col(float t, float alpha) {
        int c = Palette.color(preset.getId(), t, time * 0.3f, color.get(), color2.get());
        c = Colors.shade(c, brightness.getF() / 100f);
        return Colors.withAlpha(c, Math.max(0, Math.min(255, Math.round(255 * alpha))));
    }

    private void paint(WorldDraw d) {
        float s = size.getF();
        // лёгкое покачивание и наклон — кольцо «парит»
        double y = 0.47 + height.get() + Math.sin(time * 1.3) * 0.015;
        double tilt = Math.sin(time * 0.7) * 0.05;
        switch (style.getId()) {
            case "crown" -> crown(d, y, s);
            case "orbit" -> orbit(d, y, s, tilt);
            default -> ring(d, y, s, tilt);
        }
    }

    /** Кольцо с наклоном tilt (рад) вокруг оси X: сегменты лентами, свечение — внутренней и внешней полосой. */
    private void ring(WorldDraw d, double y, float s, double tilt) {
        double r = 0.29 * s;
        int n = 40;
        float breathe = 0.85f + 0.15f * (float) Math.sin(time * 1.7);
        // твёрдая тонкая основа (чуть светлее к центру) — видна и днём
        d.ring(0, y, 0, r - 0.012 * s, r + 0.012 * s, n, col(0.1f, 0.95f), col(0.3f, 0.95f));
        d.glowBegin();
        d.ring(0, y, 0, r - 0.07 * s, r - 0.01 * s, n, col(0.4f, 0f), col(0.2f, 0.35f * breathe));
        d.ring(0, y, 0, r + 0.01 * s, r + 0.09 * s, n, col(0.2f, 0.35f * breathe), col(0.6f, 0f));
        // бегущий блик: яркое пятно скользит по кольцу
        double a = time * 1.6;
        d.billboardDot(Math.cos(a) * r, y, Math.sin(a) * r, 0.07 * s, col(0f, 0.9f), 10);
        d.billboardDot(Math.cos(a + Math.PI) * r, y, Math.sin(a + Math.PI) * r, 0.045 * s, col(0.1f, 0.5f), 8);
        d.glowEnd();
    }

    /** Корона из света: кольцо-основа и тонкие зубцы, у каждого — сияющий кончик, зубцы мягко «дышат» по очереди. */
    private void crown(WorldDraw d, double y, float s) {
        double r = 0.24 * s;
        y -= 0.06;
        int n = 48, teeth = 8;
        d.ring(0, y, 0, r - 0.01 * s, r + 0.01 * s, n, col(0.15f, 0.95f), col(0.3f, 0.95f));
        d.glowBegin();
        d.ring(0, y, 0, r + 0.01 * s, r + 0.06 * s, n, col(0.2f, 0.3f), col(0.6f, 0f));
        for (int i = 0; i < teeth; i++) {
            double a = Math.PI * 2 * i / teeth + time * 0.25;
            float k = 0.6f + 0.4f * (float) Math.sin(time * 2.2 + i * 0.9);
            double h = (0.10 + 0.05 * (i % 2)) * s * (0.85 + 0.15 * k);
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            d.line(x, y, z, x, y + h, z, 0.018 * s, col(0.15f, 0.85f * k));
            d.line(x, y, z, x, y + h, z, 0.06 * s, col(0.3f, 0.18f * k));
            d.billboardDot(x, y + h, z, 0.045 * s, col(0f, 0.9f * k), 8);
        }
        d.glowEnd();
    }

    /** Орбита: три огонька на наклонной орбите, за каждым тает короткий след. */
    private void orbit(WorldDraw d, double y, float s, double tilt) {
        double r = 0.33 * s;
        d.glowBegin();
        d.ring(0, y, 0, r - 0.004 * s, r + 0.004 * s, 48, col(0.3f, 0.25f), col(0.3f, 0.25f));
        for (int i = 0; i < 3; i++) {
            double base = time * 1.9 + i * Math.PI * 2 / 3;
            for (int k = 4; k >= 0; k--) {
                double a = base - k * 0.17;
                double x = Math.cos(a) * r, z = Math.sin(a) * r, yy = y + Math.sin(a) * r * 0.25 + tilt * r;
                float f = 1f - k / 5f;
                d.billboardDot(x, yy, z, (0.02 + 0.035 * f) * s, col(0.1f + (1 - f) * 0.6f, (k == 0 ? 0.95f : 0.35f * f)), 8);
            }
        }
        d.glowEnd();
    }
}
