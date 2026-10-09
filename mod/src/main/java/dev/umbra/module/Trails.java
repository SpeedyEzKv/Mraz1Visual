package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Лента-след за тобой. Только твой след и только у тебя на экране. */
public final class Trails extends Module implements WorldLayer {
    private static final int MAX = 80;

    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xA0B8B4F2));
    final SliderSetting length = add(new SliderSetting("length", "Длина", 20, 4, MAX, 1, " тиков"));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.4, 0.05, 1.8, 0.05, " бл."));
    final BoolSetting thirdOnly = add(new BoolSetting("third_only", "Только от 3-го лица", true));

    private final double[] xs = new double[MAX], ys = new double[MAX], zs = new double[MAX];
    private int head, size;

    public Trails() {
        super("trails", "Trails", "След за тобой, тает со временем.", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        size = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        int len = length.getI();
        if (size > 0) {
            int last = (head - 1 + MAX) % MAX;
            double dx = p.getX() - xs[last], dy = p.getY() - ys[last], dz = p.getZ() - zs[last];
            if (dx * dx + dy * dy + dz * dz < 0.0004) { // стоим — след тает
                if (size > 0) size--;
                return;
            }
        }
        xs[head] = p.getX();
        ys[head] = p.getY();
        zs[head] = p.getZ();
        head = (head + 1) % MAX;
        size = Math.min(size + 1, len);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || size < 2) return;
        if (thirdOnly.get() && mc.options.getCameraType().isFirstPerson()) return;
        Vec3 now = p.getPosition(pt);
        int c = color.get();
        double h = height.get();
        double px = now.x, py = now.y, pz = now.z;
        float pa = 1f;
        for (int i = 1; i <= size; i++) {
            int idx = (head - i + MAX) % MAX;
            float a = 1f - i / (float) size;
            d.ribbon(px, py, pz, xs[idx], ys[idx], zs[idx], h, Colors.mulAlpha(c, pa), Colors.mulAlpha(c, a));
            px = xs[idx]; py = ys[idx]; pz = zs[idx];
            pa = a;
        }
    }
}
