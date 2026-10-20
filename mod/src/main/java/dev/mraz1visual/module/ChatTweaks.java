package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.mixin.ChatComponentAccessor;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Чат: длинная история, чат не очищается при смене сервера,
 * время у сообщений и склейка одинаковых сообщений подряд в одно «[x3]».
 */
public final class ChatTweaks extends Module {
    private static ChatTweaks instance;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    final SliderSetting history = add(new SliderSetting("history", "Строк в истории", 1000, 100, 5000, 100, ""));
    final BoolSetting keep = add(new BoolSetting("keep", "Не очищать при выходе с сервера", true));
    final BoolSetting timestamps = add(new BoolSetting("timestamps", "Время у сообщений", false));
    final BoolSetting stack = add(new BoolSetting("stack", "Склеивать повторы (антиспам)", true));

    private static String lastText;
    private static String lastShown;
    private static int repeats;

    public ChatTweaks() {
        super("chat_tweaks", "Chat Tweaks", "История чата, время сообщений и склейка спама.", Category.MISC);
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

    /** Из миксина ChatComponent.addMessage: меняет только то, как сообщение покажется у тебя. */
    public static Component decorate(Component msg, ChatComponent chat) {
        ChatTweaks c = instance;
        if (c == null || !c.isEnabled() || msg == null) return msg;
        if (!c.timestamps.get() && !c.stack.get()) return msg;

        String text = msg.getString();
        int count = 1;
        if (c.stack.get() && text.equals(lastText) && lastShown != null) {
            try {
                List<GuiMessage> all = ((ChatComponentAccessor) chat).mraz1visual$allMessages();
                if (!all.isEmpty() && all.get(0).content().getString().equals(lastShown)) {
                    all.remove(0);
                    ((ChatComponentAccessor) chat).mraz1visual$refreshTrimmedMessages();
                    count = repeats + 1;
                }
            } catch (Throwable ignored) {
            }
        }

        MutableComponent out = Component.empty();
        if (c.timestamps.get()) out.append(Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY));
        out.append(msg);
        if (count > 1) out.append(Component.literal(" [x" + count + "]").withStyle(ChatFormatting.GRAY));

        lastText = text;
        repeats = count;
        lastShown = out.getString();
        return out;
    }
}
