package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Theme;

/** Настройки самого клиента. Живут как модуль без тумблера — тот же конфиг и тот же GUI. */
public final class ClientSettings extends Module {
    public final KeySetting menuKey = add(new KeySetting("menu_key", "Открыть меню", InputConstants.KEY_RSHIFT)
            .group("Управление"));

    public final KeySetting overlayKey = add(new KeySetting("overlay_key", "Оверлей: Shift +", InputConstants.KEY_TAB)
            .group("Управление"));

    public final EnumSetting size = add(new EnumSetting("size", "Размер окна", 0,
            "auto", "Авто", "small", "Мелкий", "large", "Крупный")
            .group("Окно"));

    public final EnumSetting menuStyle = add(new EnumSetting("menu_style", "Вид меню", 0,
            "v2", "Новое", "classic", "Классическое")
            .group("Окно"));

    public final EnumSetting background = add(new EnumSetting("background", "Фон за окном", 0,
            "blur", "Размытие", "dim", "Затемнение", "none", "Нет")
            .group("Окно"));

    public final EnumSetting theme = add(new EnumSetting("theme", "Тема", 0,
            "dark", "Тёмная", "light", "Светлая")
            .group("Окно"));

    public final EnumSetting tabsLayout = add(new EnumSetting("tabs_layout", "Вкладки", 0,
            "side", "Слева (боковая панель)", "top", "Сверху")
            .group("Окно"));

    public final EnumSetting language = add(new EnumSetting("language", "Язык интерфейса", 0,
            "ru_ru", "Русский", "en_us", "English (частично)")
            .group("Окно")
            .onChange(() -> dev.mraz1visual.ui.Lang.setLanguage(ClientSettings.this.language.getId())));

    public final BoolSetting vanillaFont = add(new BoolSetting("vanilla_font", "Шрифт Minecraft (без сглаживания)", false)
            .group("Окно")
            .onChange(dev.mraz1visual.ui.Fonts::clearWidths));

    public final SliderSetting dim = add(new SliderSetting("dim", "Сила затемнения", 40, 0, 90, 5, "%")
            .group("Окно"));

    public final BoolSetting animations = add(new BoolSetting("animations", "Анимации", true)
            .group("Окно"));

    public final BoolSetting reduceMotion = add(new BoolSetting("reduce_motion", "Уменьшить движение", false)
            .group("Окно"));

    /** Один акцент на весь интерфейс; «Свой» — цвет ниже. */
    public final EnumSetting accentPreset = add(new EnumSetting("accent_preset", "Акцент", 0,
            "mraz", "Mraz1 (фиолет → голубой)", "sky", "Голубой", "violet", "Фиолетовый", "mint", "Мятный", "pink", "Розовый", "amber", "Янтарный", "graphite", "Графит", "custom", "Свой")
            .group("Цвет")
            .onChange(this::applyAccent));

    public final ColorSetting accent = add(new ColorSetting("accent", "Свой акцент", Theme.DEFAULT_ACCENT)
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

    public final ActionSetting wallpaper = add(new ActionSetting("wallpaper", "Обои Wallpaper Engine", "Выбрать ›",
            () -> net.minecraft.client.Minecraft.getInstance().setScreen(
                    new dev.mraz1visual.ui.menu.WallpaperScreen(net.minecraft.client.Minecraft.getInstance().screen)))
            .group("Обои"));

    public final BoolSetting wallMenus = add(new BoolSetting("wall_menus", "В меню игры", true)
            .group("Обои"));

    public final BoolSetting wallGui = add(new BoolSetting("wall_gui", "Внутри окна GUI", true)
            .group("Обои"));

    public final EnumSetting wallQuality = add(new EnumSetting("wall_quality", "Макс. разрешение", 1,
            "1080", "1080p", "1440", "1440p", "2160", "4K")
            .group("Обои")
            .onChange(dev.mraz1visual.wallpaper.WallpaperPlayer::stop));

    public final EnumSetting wallFps = add(new EnumSetting("wall_fps", "Кадров в секунду", 2,
            "30", "30", "60", "60", "orig", "Как в обоях")
            .group("Обои")
            .onChange(dev.mraz1visual.wallpaper.WallpaperPlayer::stop));

    public final EnumSetting wallScene = add(new EnumSetting("wall_scene", "Сцены", 0,
            "sharp", "Чёткая картинка", "anim", "Gif-превью")
            .group("Обои")
            .onChange(dev.mraz1visual.wallpaper.WallpaperPlayer::reload));

    public final SliderSetting wallDim = add(new SliderSetting("wall_dim", "Затемнение обоев", 35, 0, 90, 5, "%")
            .group("Обои"));

    public ClientSettings() {
        super("client", "Клиент", "Окно, клавиши и цвет интерфейса.", Category.CLIENT);
    }

    @Override public boolean alwaysOn() { return true; }

    public void applyAccent() {
        Theme.setAccent(switch (accentPreset.getId()) {
            case "mraz" -> 0xFF8B6CFF;
            case "violet" -> 0xFFA88BFF;
            case "mint" -> 0xFF5FE3C0;
            case "pink" -> 0xFFFF7DB4;
            case "amber" -> 0xFFFFC266;
            case "graphite" -> 0xFFB9BEC9;
            case "custom" -> accent.get();
            default -> 0xFF5AC8FA; // голубой
        });
    }
}
