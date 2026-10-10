package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Лента-след за тобой. Только твой след и только у тебя на экране. */
public final class Trails extends Module implements WorldLayer {
    private static final int MAX = 80;

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ribbon", "Лента", "line", "Светящаяся линия", "particles", "Огоньки", "both", "Лента + огоньки"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xA0B8B4F2));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xA07FD8FF));
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
        String st = style.getId();
        if ((st.equals("particles") || st.equals("both")) && size > 0 && !(thirdOnly.get() && mc.options.getCameraType().isFirstPerson())) {
            int c = dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), color.get(), color2.get(), (float) Math.random());
            dev.mraz1visual.render.FxParticles.spawn(p.getX() + (Math.random() - 0.5) * 0.4, p.getY() + Math.random() * height.get(), p.getZ() + (Math.random() - 0.5) * 0.4,
                    (Math.random() - 0.5) * 0.2, 0.15, (Math.random() - 0.5) * 0.2, 0.9f, 0.09f, c | 0xFF000000, -0.1f, true);
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
        String st = style.getId();
        if (st.equals("particles")) return;
        double h = height.get();
        boolean line = st.equals("line");
        double px = now.x, py = now.y, pz = now.z;
        float pa = 1f;
        for (int i = 1; i <= size; i++) {
            int idx = (head - i + MAX) % MAX;
            float a = 1f - i / (float) size;
            int c0 = dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), color.get(), color2.get(), (i - 1) / (float) size);
            int c1 = dev.mraz1visual.render.ColorFx.pick(colorMode.getId(), color.get(), color2.get(), i / (float) size);
            if (line) {
                double ly = h * 0.5;
                d.line(px, py + ly, pz, xs[idx], ys[idx] + ly, zs[idx], 0.06, Colors.mulAlpha(c0 | 0xFF000000, pa));
                d.line(px, py + ly, pz, xs[idx], ys[idx] + ly, zs[idx], 0.22, Colors.mulAlpha(c0, pa * 0.25f));
            } else {
                d.ribbon(px, py, pz, xs[idx], ys[idx], zs[idx], h, Colors.mulAlpha(c0, pa), Colors.mulAlpha(c1, a));
                // яркая кромка сверху
                d.line(px, py + h, pz, xs[idx], ys[idx] + h, zs[idx], 0.025, Colors.mulAlpha(c0 | 0xFF000000, pa * 0.9f));
            }
            px = xs[idx]; py = ys[idx]; pz = zs[idx];
            pa = a;
        }
    }
}
