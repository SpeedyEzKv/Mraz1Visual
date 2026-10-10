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

/** Остаточные силуэты за тобой на быстром беге и прыжках — эффект рывка. Видно от 3-го лица. */
public final class DashTrail extends Module implements WorldLayer {
    private static final int MAX = 8;
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0x70B8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0x707FD8FF));
    final SliderSetting minSpeed = add(new SliderSetting("min_speed", "С какой скорости", 5.5, 2, 12, 0.5, " бл/с"));
    final SliderSetting count = add(new SliderSetting("count", "Силуэтов", 5, 2, MAX, 1, ""));

    private final double[] xs = new double[MAX], ys = new double[MAX], zs = new double[MAX];
    private final float[] yaws = new float[MAX];
    private final long[] at = new long[MAX];
    private int head;

    public DashTrail() {
        super("dash_trail", "Dash Trail", "Силуэты-тени за тобой на быстром беге.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        double sp = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20;
        if (sp < minSpeed.get()) return;
        xs[head] = p.getX();
        ys[head] = p.getY();
        zs[head] = p.getZ();
        yaws[head] = p.yBodyRot;
        at[head] = System.currentTimeMillis();
        head = (head + 1) % MAX;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson()) return;
        long now = System.currentTimeMillis();
        int n = count.getI();
        for (int k = 1; k <= n; k++) {
            int i = (head - k + MAX) % MAX;
            float t = (now - at[i]) / 450f;
            if (at[i] == 0 || t >= 1) continue;
            int c = Colors.mulAlpha(ColorFx.pick(colorMode.getId(), color.get(), color2.get(), k / (float) n), (1 - t) * (1 - k / (float) (n + 1)));
            silhouette(d, xs[i], ys[i], zs[i], yaws[i], c);
        }
    }

    /** Упрощённая фигура игрока из повёрнутых брусков. */
    private static void silhouette(WorldDraw d, double x, double y, double z, float yaw, int c) {
        double a = Math.toRadians(yaw);
        box(d, x, y + 1.5, z, a, 0.25, 0.25, 0.25, c);      // голова
        box(d, x, y + 1.05, z, a, 0.25, 0.375, 0.125, c);   // тело
        box(d, x, y + 0.375, z, a, 0.25, 0.375, 0.125, c);  // ноги
        box(d, x + Math.cos(a) * 0.375, y + 1.05, z + Math.sin(a) * 0.375, a, 0.125, 0.375, 0.125, c);
        box(d, x - Math.cos(a) * 0.375, y + 1.05, z - Math.sin(a) * 0.375, a, 0.125, 0.375, 0.125, c);
    }

    /** Брусок с центром (x,y,z), полуразмерами hw/hh/hd, повёрнутый на a вокруг вертикали. */
    private static void box(WorldDraw d, double x, double y, double z, double a, double hw, double hh, double hd, int c) {
        double cx = Math.cos(a), sz = Math.sin(a);
        double[][] p = new double[8][3];
        for (int i = 0; i < 8; i++) {
            double lx = (i & 1) == 0 ? -hw : hw, ly = (i & 2) == 0 ? -hh : hh, lz = (i & 4) == 0 ? -hd : hd;
            p[i][0] = x + lx * cx - lz * sz;
            p[i][1] = y + ly;
            p[i][2] = z + lx * sz + lz * cx;
        }
        int[][] f = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] q : f) {
            d.quad(p[q[0]][0], p[q[0]][1], p[q[0]][2], p[q[1]][0], p[q[1]][1], p[q[1]][2],
                    p[q[2]][0], p[q[2]][1], p[q[2]][2], p[q[3]][0], p[q[3]][1], p[q[3]][2], c, c, c, c);
        }
    }
}
