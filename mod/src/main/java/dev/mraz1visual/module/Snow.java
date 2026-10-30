package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector4f;

/**
 * Снегопад только у тебя: свои снежинки (пул без аллокаций, свой цвет), дымка в тумане
 * и белый слой поверх блоков и листвы вокруг (рисуется поверх, сами блоки не меняются).
 */
public final class Snow extends Module implements WorldLayer {
    private static Snow instance;
    private static final int MAX = 900, LAYER_R = 10;

    final BoolSetting haze = add(new BoolSetting("haze", "Дымка", true));
    final BoolSetting onBlocks = add(new BoolSetting("on_blocks", "Снег на блоках", true));
    final SliderSetting density = add(new SliderSetting("density", "Плотность", 70, 0, 100, 5, " %"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет снежинок", 0xFFF4F8FF));

    private final double[] fx = new double[MAX], fy = new double[MAX], fz = new double[MAX], fs = new double[MAX], fp = new double[MAX];
    private boolean seeded;
    // снег на блоках: верх столбцов вокруг, пересчёт раз в секунду
    private final int[] topX = new int[(LAYER_R * 2 + 1) * (LAYER_R * 2 + 1)], topY = new int[topX.length], topZ = new int[topX.length];
    private int tops, scanTick;
    private final java.util.Random rnd = new java.util.Random();

    public Snow() {
        super("snow", "Snow", "Снегопад, дымка и снег на блоках — только у тебя.", Category.SKY);
        instance = this;
        markNew();
    }

    @Override
    protected void onEnable() {
        seeded = false;
        tops = 0;
    }

    /** Дымка: туман светлее и холоднее (из FogRendererMixin). */
    public static Vector4f fog(Vector4f v) {
        Snow s = instance;
        if (s == null || !s.isEnabled() || !s.haze.get() || v == null) return v;
        float k = 0.45f;
        return v.set(v.x() + (0.86f - v.x()) * k, v.y() + (0.90f - v.y()) * k, v.z() + (0.96f - v.z()) * k, v.w());
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        int n = count();
        if (!seeded) {
            for (int i = 0; i < MAX; i++) respawn(p, i, true);
            seeded = true;
        }
        for (int i = 0; i < n; i++) {
            fp[i] = fy[i];
            fy[i] -= fs[i];
            fx[i] += Math.sin((fy[i] + i) * 0.7) * 0.02;
            double dx = fx[i] - p.getX(), dz = fz[i] - p.getZ();
            if (fy[i] < p.getY() - 6 || dx * dx + dz * dz > 18 * 18) respawn(p, i, false);
        }
        if (onBlocks.get() && --scanTick <= 0) {
            scanTick = 20;
            scan(level, p);
        }
    }

    private int count() {
        return MAX * density.getI() / 100;
    }

    private void respawn(LocalPlayer p, int i, boolean anywhere) {
        double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 16;
        fx[i] = p.getX() + Math.cos(a) * r;
        fz[i] = p.getZ() + Math.sin(a) * r;
        fy[i] = p.getY() + (anywhere ? rnd.nextDouble() * 14 - 4 : 8 + rnd.nextDouble() * 4);
        fp[i] = fy[i];
        fs[i] = 0.035 + rnd.nextDouble() * 0.035;
    }

    private void scan(ClientLevel level, LocalPlayer p) {
        int px = p.getBlockX(), pz = p.getBlockZ(), py = p.getBlockY(), n = 0;
        for (int dx = -LAYER_R; dx <= LAYER_R; dx++) {
            for (int dz = -LAYER_R; dz <= LAYER_R; dz++) {
                if (dx * dx + dz * dz > LAYER_R * LAYER_R) continue;
                int x = px + dx, z = pz + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (Math.abs(y - py) > 12) continue;
                topX[n] = x;
                topY[n] = y;
                topZ[n] = z;
                n++;
            }
        }
        tops = n;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int c = color.get();
        int n = count();
        for (int i = 0; i < n; i++) {
            double y = fp[i] + (fy[i] - fp[i]) * pt;
            d.billboardDot(fx[i], y, fz[i], 0.04 + (i % 3) * 0.01, c, 6);
        }
        if (!onBlocks.get()) return;
        double ppx = mc.player.getX(), ppz = mc.player.getZ();
        for (int i = 0; i < tops; i++) {
            double x = topX[i], y = topY[i] + 0.004, z = topZ[i];
            // край слоя тает с расстоянием — граница не видна
            double dd = Math.sqrt((x + 0.5 - ppx) * (x + 0.5 - ppx) + (z + 0.5 - ppz) * (z + 0.5 - ppz));
            float a = (float) Math.max(0, Math.min(1, (LAYER_R - dd) / 3));
            if (a <= 0) continue;
            int cc = Colors.mulAlpha(0xE6F7FAFF, a);
            d.quad(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, cc, cc, cc, cc);
        }
    }
}
