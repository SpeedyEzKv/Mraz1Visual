package dev.mraz1visual.module;

import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Адрес сервера. В одиночной игре — «Одиночная игра» или скрыт. */
public final class ServerHud extends TextHud {
    final BoolSetting single = add(new BoolSetting("single", "Показывать в одиночной игре", false));
    private boolean has;

    public ServerHud() {
        super("hud_server", "Server Address", "Адрес сервера, на котором ты играешь.", 1.0, 0.14);
        labels[0] = "Сервер";
    }

    @Override
    protected void update(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        if (sd != null && !mc.hasSingleplayerServer()) {
            has = !StreamerMode.hidesCoords();
            values[0] = sd.ip;
        } else {
            has = single.get();
            values[0] = "Одиночная игра";
        }
    }

    @Override
    protected boolean visibleNow() { return has; }
}
