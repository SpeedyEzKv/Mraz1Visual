package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.desktop.DesktopScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** «Windows» в игре: по клавише открывается рабочий стол с окнами-браузерами (YouTube, Twitch…). */
public final class Desktop extends Module {
    private static Desktop instance;
    final KeySetting key = add(new KeySetting("key", "Клавиша рабочего стола", GLFW.GLFW_KEY_F9));
    final SliderSetting dim = add(new SliderSetting("dim", "Затемнение обоев", 15, 0, 80, 5, " %"));
    final BoolSetting closeOnExit = add(new BoolSetting("close_on_exit", "Закрывать окна при выходе со стола", false));
    private boolean was;

    public Desktop() {
        super("desktop", "Desktop", "Рабочий стол в игре: окна с YouTube, Twitch и любым сайтом.", Category.MISC);
        instance = this;
        loadEnabled(true);
    }

    public static Desktop instance() { return instance; }
    public double dim() { return dim.get(); }
    public boolean closeOnExit() { return closeOnExit.get(); }
    public int key() { return key.get(); }

    @Override
    public void onTick(Minecraft mc) {
        // состояние клавиши храним всегда — иначе F9, которой закрыли стол, тут же откроет его снова
        boolean down = Keys.isDown(key.get());
        if (down && !was && mc.screen == null) mc.setScreen(new DesktopScreen(null));
        was = down;
    }
}
