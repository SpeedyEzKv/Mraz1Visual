package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Friends;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Эмблема под ногами, как в Dota 2: светящийся круг с вращающимися рунами, звездой в центре
 * и поднимающимися искрами. Только картинка у тебя в клиенте.
 */
public final class Emblem extends Module implements WorldLayer {
    private static final long START = System.nanoTime();

    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0,
            "arcane", "Аркана", "inferno", "Инферно", "frost", "Лёд", "void", "Бездна", "gold", "Золото", "nature", "Природа", "custom", "Свои цвета"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1 (свои)", 0xFFB8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2 (свои)", 0xFF7FD8FF));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.6, 2, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.2, 3, 0.1, "×"));
    final BoolSetting sparks = add(new BoolSetting("sparks", "Искры вверх", true));
    final BoolSetting friends = add(new BoolSetting("friends", "У друзей тоже", false));

    public Emblem() {
        super("emblem", "Emblem", "Эмблема под ногами как в Dota 2: руны, звезда и искры.", Category.COSMETICS);
    }

    // палитра в общем буфере: заполняется каждый кадр, без новых массивов
    private final int[] palBuf = new int[2];

    private int[] palette() {
        int a, b;
        switch (style.getId()) {
            case "inferno" -> { a = 0xFFFF6A1A; b = 0xFFFFD27A; }
            case "frost" -> { a = 0xFF5FD4FF; b = 0xFFEAFBFF; }
            case "void" -> { a = 0xFF7A2BD9; b = 0xFFFF4FD8; }
            case "gold" -> { a = 0xFFF2C14E; b = 0xFFFFF4C9; }
            case "nature" -> { a = 0xFF3FD97A; b = 0xFFC8FF7A; }
            case "custom" -> { a = color.get(); b = color2.get(); }
            default -> { a = 0xFF8A5CFF; b = 0xFF6FE8FF; }
        }
        palBuf[0] = a;
        palBuf[1] = b;
        return palBuf;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float time = (System.nanoTime() - START) / 1e9f * speed.getF();
        int[] pal = palette();
        draw(d, mc.player, pt, time, pal);
        if (friends.get()) {
            for (Player p : mc.level.players()) {
                if (p != mc.player && Friends.is(p.getName().getString())) draw(d, p, pt, time + p.getId() * 0.37f, pal);
            }
        }
    }

    private void draw(WorldDraw d, Player p, float pt, float time, int[] pal) {
        if (p.isSpectator() || p.isInvisible()) return;
        double x = p.xo + (p.getX() - p.xo) * pt;
        double y = p.yo + (p.getY() - p.yo) * pt + 0.02;
        double z = p.zo + (p.getZ() - p.zo) * pt;
        double R = 1.05 * size.get();
        int c1 = pal[0], c2 = pal[1];
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 2.6);

        // мягкая подложка-свечение и двойной обод
        d.ring(x, y, z, 0, R * 0.92, 64, Colors.mulAlpha(c1, 0.10f * pulse), Colors.mulAlpha(c1, 0.32f * pulse));
        d.ring(x, y + 0.001, z, R * 0.92, R, 64, Colors.mulAlpha(c2, 0.9f), Colors.mulAlpha(c2, 0.9f));
        d.ring(x, y + 0.001, z, R, R * 1.18, 64, Colors.mulAlpha(c1, 0.45f * pulse), Colors.mulAlpha(c1, 0));
        d.ring(x, y + 0.002, z, R * 0.70, R * 0.73, 64, Colors.mulAlpha(c2, 0.7f), Colors.mulAlpha(c2, 0.7f));

        // руны по кругу между ободами — медленно вращаются
        int runes = 12;
        double rr = R * 0.815, rs = R * 0.07;
        for (int i = 0; i < runes; i++) {
            double a = time * 0.6 + i * Math.PI * 2 / runes;
            double cx = x + Math.cos(a) * rr, cz = z + Math.sin(a) * rr;
            float fl = 0.6f + 0.4f * (float) Math.sin(time * 3 + i * 1.7);
            int c = Colors.mulAlpha(i % 3 == 0 ? c2 : c1, fl);
            diamond(d, cx, y + 0.003, cz, a, rs, rs * (i % 2 == 0 ? 1.8 : 1.2), c);
        }
        // засечки снаружи — вращаются в другую сторону
        for (int i = 0; i < 36; i++) {
            double a = -time * 0.35 + i * Math.PI * 2 / 36;
            double l = i % 3 == 0 ? 0.11 : 0.05;
            strip(d, x + Math.cos(a) * (R + 0.02), y + 0.003, z + Math.sin(a) * (R + 0.02),
                    x + Math.cos(a) * (R + 0.02 + l * size.get()), y + 0.003, z + Math.sin(a) * (R + 0.02 + l * size.get()),
                    0.018, Colors.mulAlpha(c2, 0.75f));
        }

        // звезда из двух треугольников в центре — против часовой
        double sr = R * 0.62;
        for (int t = 0; t < 2; t++) {
            double base = -time * 0.9 + t * Math.PI / 3;
            for (int k = 0; k < 3; k++) {
                double a0 = base + k * Math.PI * 2 / 3, a1 = base + (k + 1) * Math.PI * 2 / 3;
                strip(d, x + Math.cos(a0) * sr, y + 0.004, z + Math.sin(a0) * sr,
                        x + Math.cos(a1) * sr, y + 0.004, z + Math.sin(a1) * sr, 0.03, Colors.mulAlpha(t == 0 ? c1 : c2, 0.85f));
            }
        }
        // ядро
        d.ring(x, y + 0.005, z, 0, R * 0.16 * pulse, 32, Colors.mulAlpha(0xFFFFFFFF, 0.85f), Colors.mulAlpha(c2, 0.2f));
        d.ring(x, y + 0.005, z, R * 0.22, R * 0.25, 32, Colors.mulAlpha(c1, 0.8f), Colors.mulAlpha(c1, 0.8f));

        // искры поднимаются от круга и гаснут
        if (sparks.get()) {
            for (int i = 0; i < 14; i++) {
                float f = ((time * 0.35f + i * 0.1373f) % 1f);
                double a = i * 2.399 + Math.floor(time * 0.35f + i * 0.1373f) * 1.3;
                double r = R * (0.3 + 0.65 * ((i * 0.618) % 1));
                d.billboardDot(x + Math.cos(a) * r, y + f * 1.6, z + Math.sin(a) * r, 0.035 + 0.02 * (1 - f),
                        Colors.mulAlpha(i % 2 == 0 ? c2 : c1, (1 - f) * 0.9f));
            }
        }
    }

    /** Плоский ромб на земле, вытянутый по радиусу. */
    private static void diamond(WorldDraw d, double cx, double y, double cz, double a, double w, double l, int c) {
        double ux = Math.cos(a), uz = Math.sin(a), vx = -uz, vz = ux;
        d.quad(cx + ux * l, y, cz + uz * l, cx + vx * w, y, cz + vz * w,
                cx - ux * l, y, cz - uz * l, cx - vx * w, y, cz - vz * w, c, c, c, c);
    }

    /** Плоская полоса на земле от точки до точки. */
    private static void strip(WorldDraw d, double x1, double y1, double z1, double x2, double y2, double z2, double w, int c) {
        double dx = x2 - x1, dz = z2 - z1, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;
        double nx = -dz / len * w / 2, nz = dx / len * w / 2;
        d.quad(x1 + nx, y1, z1 + nz, x2 + nx, y2, z2 + nz, x2 - nx, y2, z2 - nz, x1 - nx, y1, z1 - nz, c, c, c, c);
    }
}
