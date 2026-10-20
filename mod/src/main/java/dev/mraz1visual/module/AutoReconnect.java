package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** После вылета с сервера сам заходит обратно через несколько секунд. */
public final class AutoReconnect extends Module {
    private static AutoReconnect instance;
    final SliderSetting delay = add(new SliderSetting("delay", "Задержка", 5, 1, 30, 1, " с"));
    final SliderSetting attempts = add(new SliderSetting("attempts", "Попыток подряд", 5, 1, 20, 1, ""));

    private ServerData last;
    private long shownAt = -1;
    private int tries;
    private boolean cancelled;
    private Object screenSeen;

    public AutoReconnect() {
        super("auto_reconnect", "Auto Reconnect", "Сам переподключается к серверу после вылета.", Category.MISC);
        instance = this;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen)) return;
            AutoReconnect a = instance;
            if (a == null || !a.isEnabled()) return;
            Screens.getButtons(screen).add(Button.builder(Component.literal("Отменить автоподключение"), b -> {
                a.cancelled = true;
                b.active = false;
            }).bounds(w / 2 - 100, 26, 200, 20).build());
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                String text = a.statusText();
                if (text != null) g.drawCenteredString(client.font, text, w / 2, 12, 0xFFB8B4F2);
            });
        });
    }

    private boolean willReconnect() {
        return isEnabled() && !cancelled && last != null && tries < attempts.getI() && !AutoLeave.leftRecently();
    }

    private String statusText() {
        if (!willReconnect() || shownAt < 0) return null;
        long left = Math.max(0, Math.round(delay.get() * 1000) - (System.currentTimeMillis() - shownAt));
        return "Переподключение к " + last.ip + " через " + (left + 999) / 1000 + " с  (" + (tries + 1) + "/" + attempts.getI() + ")";
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != null && mc.getCurrentServer() != null) {
            last = mc.getCurrentServer();
            tries = 0;
            cancelled = false;
            shownAt = -1;
            screenSeen = null;
            return;
        }
        if (!(mc.screen instanceof DisconnectedScreen)) {
            shownAt = -1;
            screenSeen = null;
            return;
        }
        if (screenSeen != mc.screen) { screenSeen = mc.screen; shownAt = System.currentTimeMillis(); }
        if (!willReconnect()) return;
        if (System.currentTimeMillis() - shownAt < delay.get() * 1000) return;

        tries++;
        shownAt = -1;
        ServerData data = last;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
            ServerAddress.parseString(data.ip), data, false, null);
    }
}
