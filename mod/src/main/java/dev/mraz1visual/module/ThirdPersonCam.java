package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Камера от третьего лица: своё расстояние и плавный отъезд при нажатии F5. Сквозь стены не смотрит. */
public final class ThirdPersonCam extends Module {
    private static ThirdPersonCam instance;
    final SliderSetting distance = add(new SliderSetting("distance", "Расстояние", 4, 1.5, 12, 0.25, " бл."));
    final BoolSetting smooth = add(new BoolSetting("smooth", "Плавный отъезд", true));

    private boolean wasFirst = true;
    private long since;

    public ThirdPersonCam() {
        super("third_person", "Third Person", "Своё расстояние камеры F5 и плавный отъезд.", Category.VISUALS);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean first = mc.options.getCameraType().isFirstPerson();
        if (wasFirst && !first) since = System.currentTimeMillis();
        wasFirst = first;
    }

    /** Из миксина Camera.setup: длина отъезда камеры (ваниль — 4). */
    public static float distance(float original) {
        ThirdPersonCam c = instance;
        if (c == null || !c.isEnabled()) return original;
        float d = original / 4f * c.distance.getF();
        if (c.smooth.get()) {
            float t = Math.min(1f, (System.currentTimeMillis() - c.since) / 300f);
            d *= 0.25f + 0.75f * (1 - (1 - t) * (1 - t) * (1 - t));
        }
        return d;
    }
}
