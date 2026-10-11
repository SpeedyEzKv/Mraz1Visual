package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Theme;
import org.lwjgl.glfw.GLFW;

/** Настройки самого клиента. Живут как модуль без тумблера — тот же конфиг и тот же GUI. */
public final class ClientSettings extends Module {
    public final KeySetting menuKey = add(new KeySetting("menu_key", "Открыть меню", GLFW.GLFW_KEY_RIGHT_SHIFT)
            .group("Управление"));

    public final KeySetting overlayKey = add(new KeySetting("overlay_key", "Оверлей: Shift +", GLFW.GLFW_KEY_TAB)
            .group("Управление"));

    public final EnumSetting size = add(new EnumSetting("size", "Размер окна", 0,
            "auto", "Авто", "small", "Мелкий", "large", "Крупный")
            .group("Окно"));

    public final EnumSetting background = add(new EnumSetting("background", "Фон за окном", 1,
            "blur", "Размытие", "dim", "Затемнение", "none", "Нет")
            .group("Окно"));

    public final SliderSetting dim = add(new SliderSetting("dim", "Сила затемнения", 40, 0, 90, 5, "%")
            .group("Окно"));

    public final BoolSetting animations = add(new BoolSetting("animations", "Анимации", true)
            .group("Окно"));

    public final ColorSetting accent = add(new ColorSetting("accent", "Акцент", Theme.DEFAULT_ACCENT)
            .group("Цвет")
            .onChange(this::applyAccent));

    public final BoolSetting customTitle = add(new BoolSetting("custom_title", "Своё главное меню", true)
            .group("Меню игры"));

    public final BoolSetting customServers = add(new BoolSetting("custom_servers", "Свой список серверов", true)
            .group("Меню игры"));

    public final BoolSetting customOptions = add(new BoolSetting("custom_options", "Свои настройки игры", true)
            .group("Меню игры"));

    public final BoolSetting customWorlds = add(new BoolSetting("custom_worlds", "Свой выбор миров", true)
            .group("Меню игры"));

    public final BoolSetting customPause = add(new BoolSetting("custom_pause", "Своё меню паузы и статистика", true)
            .group("Меню игры"));

    public ClientSettings() {
        super("client", "Клиент", "Окно, клавиши и цвет интерфейса.", Category.CLIENT);
    }

    @Override public boolean alwaysOn() { return true; }

    public void applyAccent() { Theme.setAccent(accent.get()); }
}
