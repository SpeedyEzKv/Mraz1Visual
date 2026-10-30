package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

/**
 * Сумерки: тёмное фиолетово-синее небо и туман, но свет блоков и видимость не трогаются —
 * мобы и блоки видно как обычно. Время суток — только у тебя (в 26.x время мира идёт через «часы мира», там только цвета).
 */
public final class Twilight extends Module {
    private static Twilight instance;
    final SliderSetting hour = add(new SliderSetting("hour", "Время суток", 19.5, 17, 23, 0.25, ":00")
            .format(v -> (int) v + ":" + String.format(java.util.Locale.ROOT, "%02d", Math.round((v - (int) v) * 60))));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 60, 20, 100, 5, " %"));

    public Twilight() {
        super("twilight", "Сумерки", "Тёмная атмосфера неба и тумана без потери видимости.", Category.SKY);
        instance = this;
        markNew();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) return;
        long ticks = Math.round(((hour.get() - 6 + 24) % 24) * 1000);
        //? if <26.1
        mc.level.getLevelData().setDayTime(ticks);
    }

    private static boolean on() {
        return instance != null && instance.isEnabled();
    }

    /** Цвет тумана: к сумеречному сиреневому, светлота — по ползунку. */
    public static Vector4f fog(Vector4f v) {
        if (!on() || v == null) return v;
        float b = instance.brightness.getF() / 100f, k = 0.6f;
        return v.set(v.x() + (0.36f * b - v.x()) * k, v.y() + (0.28f * b - v.y()) * k, v.z() + (0.52f * b - v.z()) * k, v.w());
    }

    /** Цвет купола неба (RGB int). */
    public static int sky(int rgb) {
        if (!on()) return rgb;
        float b = instance.brightness.getF() / 100f, k = 0.7f;
        int r = Math.round((rgb >> 16 & 255) + (0x2A * b - (rgb >> 16 & 255)) * k);
        int g = Math.round((rgb >> 8 & 255) + (0x22 * b - (rgb >> 8 & 255)) * k);
        int bl = Math.round((rgb & 255) + (0x5A * b - (rgb & 255)) * k);
        return (rgb & 0xFF000000) | Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, bl));
    }
}
