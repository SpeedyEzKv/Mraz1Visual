package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.BodyFrame;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Аксессуары на голову: корона, кошачьи ушки, сердечки вокруг головы. Видно только тебе, от 3-го лица. */
public final class Accessories extends Module implements WorldLayer {
    final BoolSetting crown = add(new BoolSetting("crown", "Корона", true));
    final ColorSetting crownColor = add(new ColorSetting("crown_color", "Цвет короны", 0xF0F2C94C));
    final BoolSetting ears = add(new BoolSetting("ears", "Кошачьи ушки", false));
    final ColorSetting earsColor = add(new ColorSetting("ears_color", "Цвет ушек", 0xF0303040));
    final BoolSetting hearts = add(new BoolSetting("hearts", "Сердечки вокруг головы", false));

    private final BodyFrame f = new BodyFrame();

    public Accessories() {
        super("accessories", "Accessories", "Корона, ушки и сердечки на голове.", Category.COSMETICS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        f.head(p, pt);
        if (crown.get()) crown(d);
        if (ears.get()) ears(d);
        if (hearts.get()) hearts(d, f);
    }

    private void crown(WorldDraw d) {
        int c = crownColor.get(), dark = Colors.lerp(c, 0xFF5A3A08, 0.45f);
        int n = 16;
        double r = 0.27, y0 = 0.5, y1 = 0.58;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double x0 = Math.cos(a0) * r, z0 = Math.sin(a0) * r, x1 = Math.cos(a1) * r, z1 = Math.sin(a1) * r;
            d.quad(f.wx(x0, y0, z0), f.wy(x0, y0, z0), f.wz(x0, y0, z0), f.wx(x1, y0, z1), f.wy(x1, y0, z1), f.wz(x1, y0, z1),
                    f.wx(x1, y1, z1), f.wy(x1, y1, z1), f.wz(x1, y1, z1), f.wx(x0, y1, z0), f.wy(x0, y1, z0), f.wz(x0, y1, z0), dark, dark, c, c);
            if (i % 2 == 0) { // зубец
                double am = (a0 + a1) / 2 + Math.PI / n, xm = Math.cos(am) * r, zm = Math.sin(am) * r;
                double xe = Math.cos(a0 + Math.PI * 4 / n) * r, ze = Math.sin(a0 + Math.PI * 4 / n) * r;
                d.tri(f.wx(x0, y1, z0), f.wy(x0, y1, z0), f.wz(x0, y1, z0), f.wx(xe, y1, ze), f.wy(xe, y1, ze), f.wz(xe, y1, ze),
                        f.wx(xm, y1 + 0.1, zm), f.wy(xm, y1 + 0.1, zm), f.wz(xm, y1 + 0.1, zm), c, c, Colors.lerp(c, 0xFFFFFFFF, 0.4f));
                d.billboardDot(f.wx(xm, y1 + 0.1, zm), f.wy(xm, y1 + 0.1, zm), f.wz(xm, y1 + 0.1, zm), 0.025, 0xFFFF6A8A, 6);
            }
        }
    }

    private void ears(WorldDraw d) {
        int c = earsColor.get(), inner = 0xF0F2A0B8;
        for (int s = -1; s <= 1; s += 2) {
            double bx0 = s * 0.08, bx1 = s * 0.25, tipX = s * 0.2, y = 0.5, z = 0.0;
            d.tri(f.wx(bx0, y, z), f.wy(bx0, y, z), f.wz(bx0, y, z), f.wx(bx1, y, z), f.wy(bx1, y, z), f.wz(bx1, y, z),
                    f.wx(tipX, y + 0.2, z), f.wy(tipX, y + 0.2, z), f.wz(tipX, y + 0.2, z), c, c, c);
            double ix0 = s * 0.115, ix1 = s * 0.215, it = s * 0.19, zf = 0.01;
            d.tri(f.wx(ix0, y + 0.015, zf), f.wy(ix0, y + 0.015, zf), f.wz(ix0, y + 0.015, zf), f.wx(ix1, y + 0.015, zf), f.wy(ix1, y + 0.015, zf),
                    f.wz(ix1, y + 0.015, zf), f.wx(it, y + 0.15, zf), f.wy(it, y + 0.15, zf), f.wz(it, y + 0.15, zf), inner, inner, inner);
        }
    }

    private static void hearts(WorldDraw d, BodyFrame f) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 3; i++) {
            double a = t * 1.4 + i * Math.PI * 2 / 3;
            double x = Math.cos(a) * 0.45, z = Math.sin(a) * 0.45, y = 0.35 + Math.sin(t * 2 + i) * 0.08;
            float pulse = 0.9f + 0.15f * (float) Math.sin(t * 6 + i);
            d.label(Component.literal("❤").withStyle(ChatFormatting.RED), f.wx(x, y, z), f.wy(x, y, z), f.wz(x, y, z), 0.9f * pulse, 0xFFFFFFFF, 0);
        }
    }
}
