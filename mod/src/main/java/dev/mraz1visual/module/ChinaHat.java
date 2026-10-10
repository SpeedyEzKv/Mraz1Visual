package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Конус-шляпа над твоей головой. Виден от третьего лица. */
public final class ChinaHat extends Module implements WorldLayer {
    final ColorSetting base = add(new ColorSetting("base", "Цвет края", 0x80B8B4F2));
    final ColorSetting tip = add(new ColorSetting("tip", "Цвет верха", 0xE0FFFFFF));
    final SliderSetting radius = add(new SliderSetting("radius", "Ширина", 0.65, 0.3, 1.2, 0.05, " бл."));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.3, 0.1, 0.6, 0.05, " бл."));
    final dev.mraz1visual.setting.EnumSetting colorMode = add(new dev.mraz1visual.setting.EnumSetting("color_mode", "Режим цвета", 0, "solid", "Как выбрано", "rainbow", "Радуга по кругу"));
    final dev.mraz1visual.setting.BoolSetting rim = add(new dev.mraz1visual.setting.BoolSetting("rim", "Светящийся край", true));

    public ChinaHat() {
        super("china_hat", "China Hat", "Полупрозрачная шляпа-конус над головой.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        Vec3 pos = p.getPosition(pt);
        double x = pos.x, y = pos.y + p.getBbHeight() + 0.04 - (p.isCrouching() ? 0.3 : 0), z = pos.z;
        double r = radius.get(), h = height.get();
        if (colorMode.getId().equals("rainbow")) {
            // радужный конус: каждый сектор своего цвета, цвета плывут по кругу
            int n = 48;
            float time = System.currentTimeMillis() / 4000f;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                int c = 0xFF000000 | dev.mraz1visual.ui.Colors.hsv((i / (float) n + time) % 1f, 0.5f, 1f);
                int cb = dev.mraz1visual.ui.Colors.withAlpha(c, base.get() >>> 24), ct = dev.mraz1visual.ui.Colors.withAlpha(c, tip.get() >>> 24);
                d.quad(x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r,
                        x, y + h, z, x, y + h, z, cb, cb, ct, ct);
            }
        } else {
            d.cone(x, y, z, r, h, 48, base.get(), tip.get());
        }
        if (rim.get()) d.ring(x, y, z, r - 0.03, r + 0.01, 48, dev.mraz1visual.ui.Colors.withAlpha(base.get(), 0xFF), dev.mraz1visual.ui.Colors.withAlpha(base.get(), 0x60));
    }
}
