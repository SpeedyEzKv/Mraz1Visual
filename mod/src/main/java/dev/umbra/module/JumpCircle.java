package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Расходящийся круг на земле в точке прыжка. Видишь только ты. */
public final class JumpCircle extends Module implements WorldLayer {
    private static final int MAX = 8;

    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xD0B8B4F2));
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
        if (wasOnGround && !onGround && p.getDeltaMovement().y > 0.1) {
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
            int c = Colors.mulAlpha(base, (float) (1 - t));
            d.ring(cx[i], cy[i] + 0.02, cz[i], r * 0.8, r, 48, Colors.mulAlpha(c, 0.15f), c);
        }
    }
}
