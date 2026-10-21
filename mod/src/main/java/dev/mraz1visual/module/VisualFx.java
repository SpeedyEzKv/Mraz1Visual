package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;

/** Общие настройки всех визуалов: качество, яркость, «Без вспышек» и один пресет на всё. Всегда включён. */
public final class VisualFx extends Module {
    public final EnumSetting quality = add(new EnumSetting("quality", "Качество эффектов", 2,
            "low", "Низкое", "medium", "Среднее", "high", "Высокое", "ultra", "Ультра")
            .hint("Сколько частиц, сегментов у колец и лент. На слабом ПК — «Среднее»."));
    public final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость эффектов", 100, 30, 130, 5, " %"));
    public final BoolSetting noFlash = add(new BoolSetting("no_flash", "Без вспышек", false)
            .hint("Убирает резкие вспышки и мерцание: удары, убийства и тотемы светятся мягко."));
    public final BoolSetting sync = add(new BoolSetting("sync", "Один пресет на все визуалы", false).group("Палитра"));
    public final EnumSetting preset = add(new EnumSetting("preset", "Пресет", 0, Palette.OPTIONS).group("Палитра"));
    public final ColorSetting color1 = add(new ColorSetting("color1", "Свой: центр", 0xFFB8E6FF).group("Палитра"));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Свой: край", 0xFF7A6BFF).group("Палитра"));

    private static VisualFx instance;

    public VisualFx() {
        super("visual_fx", "Эффекты: общее", "Качество, яркость, «Без вспышек» и один цвет на все визуалы.", Category.VISUALS);
        instance = this;
    }

    public static VisualFx get() {
        return instance;
    }

    @Override
    public boolean alwaysOn() {
        return true;
    }
}
