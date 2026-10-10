package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;

/** Чат: длинная история вместо 100 строк и чат не очищается при смене сервера. */
public final class ChatTweaks extends Module {
    private static ChatTweaks instance;
    final SliderSetting history = add(new SliderSetting("history", "Строк в истории", 1000, 100, 5000, 100, ""));
    final BoolSetting keep = add(new BoolSetting("keep", "Не очищать при выходе с сервера", true));

    public ChatTweaks() {
        super("chat_tweaks", "Chat Tweaks", "Длинная история чата, чат не очищается.", Category.MISC);
        instance = this;
    }

    public static int history(int vanilla) {
        ChatTweaks c = instance;
        return c != null && c.isEnabled() ? Math.max(vanilla, c.history.getI()) : vanilla;
    }

    public static boolean keep() {
        ChatTweaks c = instance;
        return c != null && c.isEnabled() && c.keep.get();
    }
}
