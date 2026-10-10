package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;

/** Своя музыка игры: например, музыка меню или Энда прямо в обычном мире. */
public final class WorldMusic extends Module {
    private static WorldMusic instance;
    final EnumSetting track = add(new EnumSetting("track", "Музыка", 0,
            "menu", "Главное меню", "creative", "Креатив", "end", "Энд", "boss", "Дракон", "water", "Под водой", "credits", "Титры"));

    public WorldMusic() {
        super("world_music", "World Music", "Музыка меню, Энда или титров в любом мире.", Category.VISUALS);
        instance = this;
    }

    /** Из миксина Minecraft.getSituationalMusic. */
    public static Music apply(Music original) {
        WorldMusic w = instance;
        if (w == null || !w.isEnabled()) return original;
        return switch (w.track.getId()) {
            case "menu" -> Musics.MENU;
            case "creative" -> Musics.CREATIVE;
            case "end" -> Musics.END;
            case "boss" -> Musics.END_BOSS;
            case "water" -> Musics.UNDER_WATER;
            case "credits" -> Musics.CREDITS;
            default -> original;
        };
    }
}
