package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.KeySetting;
import dev.umbra.setting.SliderSetting;

/**
 * Витрина компонентов для проверки GUI. Ничего не рисует в мире.
 * Регистрируется только в dev-окружении. Заодно — образец, как писать модуль.
 */
public final class DemoModule extends Module {
    final BoolSetting toggle = add(new BoolSetting("toggle", "Тумблер", true));
    final SliderSetting slider = add(new SliderSetting("slider", "Слайдер", 50, 0, 100, 1, "%"));
    final SliderSetting fine = add(new SliderSetting("fine", "Дробный", 1.5, 0.5, 3, 0.1, "×"));
    final EnumSetting seg = add(new EnumSetting("seg", "Качество", 1, "low", "Лёгкое", "full", "Полное")
            .group("Выбор"));
    final EnumSetting list = add(new EnumSetting("list", "Список", 0,
            "a", "Вариант A", "b", "Вариант B", "c", "Вариант C", "d", "Вариант D", "e", "Вариант E")
            .group("Выбор"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xCCFFFFFF).group("Цвет"));
    final KeySetting key = add(new KeySetting("key", "Клавиша", KeySetting.NONE).group("Цвет"));

    public DemoModule() {
        super("demo", "Demo", "Все компоненты интерфейса на одной странице.", Category.VISUALS);
    }
}
