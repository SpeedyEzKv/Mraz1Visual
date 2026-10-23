package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.music.player.Library;
import dev.mraz1visual.music.player.MusicPlayer;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Встроенный музыкальный плеер: локальные треки (mp3, ogg, flac, wav) из папки mraz1visual/music и своих папок.
 * Свой звук — отдельно от громкости игры. Всегда доступен; сам экран — кнопкой «Открыть плеер».
 */
public final class MusicPlayerModule extends Module {
    final ActionSetting open = add(new ActionSetting("open", "Плеер и библиотека", "Открыть",
            () -> Minecraft.getInstance().setScreen(new dev.mraz1visual.ui.MusicScreen(Minecraft.getInstance().screen))));
    final SliderSetting volume = add(new SliderSetting("volume", "Громкость плеера", 60, 0, 100, 1, " %"));
    final EnumSetting eq = add(new EnumSetting("eq", "Эквалайзер", 0, MusicPlayer.EQ));
    final SliderSetting crossfade = add(new SliderSetting("crossfade", "Кроссфейд", 3, 0, 10, 0.5, " с"));
    final BoolSetting shuffle = add(new BoolSetting("shuffle", "Перемешивание", false));
    final EnumSetting repeat = add(new EnumSetting("repeat", "Повтор", 0, MusicPlayer.REPEAT));
    final ActionSetting addFolder = add(new ActionSetting("add_folder", "Добавить папку с музыкой", "Выбрать", MusicPlayerModule::pickFolder).group("Библиотека"));
    final ActionSetting rescan = add(new ActionSetting("rescan", "Обновить библиотеку", "Обновить",
            () -> Library.rescan(new ArrayList<>(MusicPlayer.folders))).group("Библиотека"));
    final ActionSetting openFolder = add(new ActionSetting("open_folder", "Папка mraz1visual/music", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.openFolder(Library.DEFAULT)).group("Библиотека"));

    private static boolean loaded;
    private static MusicPlayerModule instance;

    public MusicPlayerModule() {
        super("music_player", "Music Player", "Свой плеер: локальная музыка, плейлисты, эквалайзер, кроссфейд, тексты.", Category.MUSIC);
        instance = this;
    }

    @Override
    public boolean alwaysOn() {
        return true;
    }

    public static MusicPlayerModule get() {
        return instance;
    }

    /** Первая загрузка: настройки, последняя очередь, скан библиотеки — в фоне. */
    public static void init() {
        if (loaded) return;
        loaded = true;
        MusicPlayer.load();
        Library.rescan(new ArrayList<>(MusicPlayer.folders));
    }

    @Override
    public void onTick(Minecraft mc) {
        init();
        // настройки → плеер (дёшево: просто поля)
        MusicPlayer.volume = volume.getF() / 100f;
        MusicPlayer.eq = eq.get();
        MusicPlayer.crossfadeSec = crossfade.getF();
        MusicPlayer.shuffle = shuffle.get();
        MusicPlayer.repeat = repeat.get();
    }

    /** Из экрана плеера: настройки меняются и там. */
    public void setVolume(float v) { volume.set(v * 100); }

    public void cycleRepeat() { repeat.set((repeat.get() + 1) % repeat.size()); }

    public void toggleShuffle() { shuffle.set(!shuffle.get()); }

    public void cycleEq() { eq.set((eq.get() + 1) % eq.size()); }

    public String eqLabel() { return eq.label(); }

    private static void pickFolder() {
        // системный диалог выбора папки — в фоне, чтобы не держать кадр
        Thread t = new Thread(() -> {
            String res = dev.mraz1visual.compat.Platform.pickFolder("Папка с музыкой", dev.mraz1visual.util.TextFiles.DIR.resolve("music"));
            if (res != null && !res.isBlank()) MusicPlayer.addFolder(Path.of(res));
        }, "Mraz1Visual folder dialog");
        t.setDaemon(true);
        t.start();
    }

    public static void pickFolderFromUi() { pickFolder(); }
}
