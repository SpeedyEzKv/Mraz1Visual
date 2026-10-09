package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Конус-шляпа над твоей головой. Виден от третьего лица. */
public final class ChinaHat extends Module implements WorldLayer {
    final ColorSetting base = add(new ColorSetting("base", "Цвет края", 0x80B8B4F2));
    final ColorSetting tip = add(new ColorSetting("tip", "Цвет верха", 0xE0FFFFFF));
    final SliderSetting radius = add(new SliderSetting("radius", "Ширина", 0.65, 0.3, 1.2, 0.05, " бл."));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.3, 0.1, 0.6, 0.05, " бл."));

    public ChinaHat() {
        super("china_hat", "China Hat", "Полупрозрачная шляпа-конус над головой.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        Vec3 pos = p.getPosition(pt);
        d.cone(pos.x, pos.y + p.getBbHeight() + 0.04, pos.z, radius.get(), height.get(), 40, base.get(), tip.get());
    }
}
