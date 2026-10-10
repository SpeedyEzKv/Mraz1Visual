package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.BodyFrame;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Крылья за спиной: машут медленно на месте и быстрее в прыжке и полёте. Только у тебя, от 3-го лица. */
public final class Wings extends Module implements WorldLayer {
    // контур крыла: (вдоль размаха 0..1, по высоте), от корня по часовой
    private static final double[][] ANGEL = {{0, 0}, {0.18, 0.32}, {0.45, 0.52}, {0.78, 0.6}, {1.0, 0.5}, {0.92, 0.25},
            {0.78, 0.02}, {0.66, -0.18}, {0.5, -0.36}, {0.32, -0.5}, {0.16, -0.42}, {0.05, -0.18}};
    private static final double[][] DEMON = {{0, 0}, {0.25, 0.45}, {0.55, 0.62}, {1.0, 0.7}, {0.82, 0.35}, {0.88, 0.05},
            {0.66, 0.0}, {0.68, -0.28}, {0.45, -0.2}, {0.4, -0.45}, {0.18, -0.25}, {0.05, -0.1}};
    private static final double[][] BUTTERFLY = {{0, 0}, {0.2, 0.45}, {0.55, 0.7}, {0.85, 0.6}, {0.8, 0.25}, {0.55, 0.05},
            {0.75, -0.15}, {0.7, -0.45}, {0.45, -0.55}, {0.2, -0.35}, {0.05, -0.1}};

    final EnumSetting shape = add(new EnumSetting("shape", "Форма", 0, "angel", "Ангел", "demon", "Демон", "butterfly", "Бабочка"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xB0F2F0FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xB0B8B4F2));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 1.8, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Взмахи", 1, 0.3, 3, 0.1, "×"));

    private final BodyFrame frame = new BodyFrame();
    private float phase;
    private long last;

    public Wings() {
        super("wings", "Wings", "Крылья за спиной: ангел, демон или бабочка.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        boolean air = !p.onGround();
        phase += dt * speed.getF() * (air ? 9f : 2.4f);
        frame.body(p, pt);
        double[][] outline = switch (shape.getId()) {
            case "demon" -> DEMON;
            case "butterfly" -> BUTTERFLY;
            default -> ANGEL;
        };
        double span = 1.15 * size.get(), height = 0.95 * size.get();
        // угол «назад» от плоскости спины: 15° сложены … 55° раскрыты
        double flap = Math.toRadians(35 + 20 * Math.sin(phase));
        for (int side = -1; side <= 1; side += 2) {
            double rootR = side * 0.08, rootF = -0.16;
            double dr = side * Math.cos(flap), df = -Math.sin(flap);
            double[] wx = new double[outline.length], wy = new double[outline.length], wz = new double[outline.length];
            for (int i = 0; i < outline.length; i++) {
                double u = outline[i][0] * span, v = outline[i][1] * height;
                double r = rootR + dr * u, f = rootF + df * u - 0.06 * u * u;
                wx[i] = frame.wx(r, v, f);
                wy[i] = frame.wy(r, v, f);
                wz[i] = frame.wz(r, v, f);
            }
            // центр для веера
            double cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < outline.length; i++) { cx += wx[i]; cy += wy[i]; cz += wz[i]; }
            cx /= outline.length; cy /= outline.length; cz /= outline.length;
            int cIn = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), 0f);
            int n = outline.length;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int cOut = ColorFx.pick(colorMode.getId(), color.get(), color2.get(), i / (float) n);
                d.tri(cx, cy, cz, wx[i], wy[i], wz[i], wx[j], wy[j], wz[j], Colors.mulAlpha(cIn, 0.55f), cOut, cOut);
                d.line(wx[i], wy[i], wz[i], wx[j], wy[j], wz[j], 0.025, Colors.withAlpha(cOut, 0xF0));
            }
            // «перья» — линии от корня к краю
            for (int i = 2; i < n - 1; i += 2) {
                d.line(wx[0], wy[0], wz[0], wx[i], wy[i], wz[i], 0.015, Colors.mulAlpha(Colors.withAlpha(cIn, 0xFF), 0.5f));
            }
        }
    }
}
