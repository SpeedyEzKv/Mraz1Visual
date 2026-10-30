package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PvP Safe: пока сервер пишет, что ты в режиме PvP («Вы в режиме PvP… 15 сек» в чате или боссбаре),
 * кнопка «Отключиться» и закрытие окна игры сначала спрашивают «Остаться / Всё равно выйти».
 * Таймер берётся из сообщения сервера, на сервер ничего не отправляется.
 */
public final class PvpSafe extends Module {
    private static PvpSafe instance;
    /** «PvP … 15 сек», «PVP: 12с», «режиме ПВП 9 секунд». */
    private static final Pattern TIMER = Pattern.compile("(?iu)(pvp|пвп)\\D{0,40}?(\\d{1,3})\\s*(с|сек|s)");

    final BoolSetting window = add(new BoolSetting("window", "Спрашивать и при закрытии окна игры", true));

    private static long combatUntil;
    /** «Всё равно выйти» — следующий выход без вопроса. */
    public static boolean bypass;
    private static boolean asking;

    public PvpSafe() {
        super("pvp_safe", "PvP Safe", "Подтверждение выхода, пока ты в режиме PvP.", Category.MISC);
        instance = this;
        markNew();
    }

    /** Текст из чата или боссбара. */
    public static void onText(String s) {
        PvpSafe p = instance;
        if (p == null || !p.isEnabled() || s == null || s.isEmpty()) return;
        String low = s.toLowerCase(Locale.ROOT);
        if (!low.contains("pvp") && !low.contains("пвп")) return;
        Matcher m = TIMER.matcher(s);
        if (m.find()) {
            int sec = Integer.parseInt(m.group(2));
            combatUntil = Math.max(combatUntil, System.currentTimeMillis() + sec * 1000L);
        } else if (low.contains("вышли из режима") || low.contains("больше не в режиме")) {
            combatUntil = 0;
        }
    }

    public static boolean inCombat() {
        PvpSafe p = instance;
        return p != null && p.isEnabled() && !bypass && System.currentTimeMillis() < combatUntil && Minecraft.getInstance().level != null;
    }

    public static int secondsLeft() {
        return (int) Math.max(0, (combatUntil - System.currentTimeMillis() + 999) / 1000);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) {
            combatUntil = 0;
            bypass = false;
            return;
        }
        // боссбары: FunTime показывает таймер PvP там
        try {
            var overlay = dev.mraz1visual.compat.Mc.bossOverlay(mc);
            for (var e : ((dev.mraz1visual.mixin.BossHealthOverlayAccessor) overlay).mraz1visual$events().values()) onText(e.getName().getString());
        } catch (Throwable ignored) {
        }
    }

    /** Окно игры закрывают в бою (из миксина окна): спросить вместо выхода. */
    public static boolean blocksWindowClose() {
        PvpSafe p = instance;
        if (!inCombat() || !p.window.get()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!asking) mc.execute(() -> ask(mc, () -> {
            bypass = true;
            mc.stop();
        }));
        return true;
    }

    /** Кнопка выхода нажата в бою: открыть вопрос. true — выход перехвачен. */
    public static boolean confirmQuit(Runnable quit) {
        if (!inCombat()) return false;
        Minecraft mc = Minecraft.getInstance();
        ask(mc, () -> {
            bypass = true;
            quit.run();
        });
        return true;
    }

    private static void ask(Minecraft mc, Runnable leave) {
        asking = true;
        Screen back = mc.screen;
        mc.setScreen(new dev.mraz1visual.ui.ConfirmScreen("Ты в режиме PvP",
                "Осталось " + secondsLeft() + " с. Выход сейчас обычно = смерть и потеря вещей.",
                "Остаться", "Всё равно выйти", () -> {
            asking = false;
            mc.setScreen(back);
        }, () -> {
            asking = false;
            leave.run();
        }));
    }

    /** Выход так же, как кнопка ванильного меню паузы (сохранение мира, закрытие соединения). */
    public static void vanillaQuit(Minecraft mc) {
        try {
            PauseScreen vanilla = new PauseScreen(true);
            vanilla.init(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            for (var child : vanilla.children()) {
                if (child instanceof Button b && isQuitButton(b)) {
                    b.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    return;
                }
            }
        } catch (Throwable t) {
            ModuleManager.LOG.error("PvP Safe: quit failed", t);
        }
    }

    private static boolean isQuitButton(Button b) {
        String msg = b.getMessage().getString();
        return msg.equals(Component.translatable("menu.returnToMenu").getString()) || msg.equals(Component.translatable("menu.disconnect").getString());
    }

    /** Ванильное меню паузы: клик по «Отключиться» в бою — сначала вопрос. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof PauseScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
                if (!inCombat() || e.button() != 0) return true;
                for (var child : s.children()) {
                    if (child instanceof Button b && isQuitButton(b) && b.isMouseOver(e.x(), e.y())) {
                        confirmQuit(() -> vanillaQuit(client));
                        return false;
                    }
                }
                return true;
            });
        });
    }
}
