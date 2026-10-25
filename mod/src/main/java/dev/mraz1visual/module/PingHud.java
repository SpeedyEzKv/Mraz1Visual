package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Пинг до сервера (из таба). В одиночной игре скрыт. */
public final class PingHud extends TextHud {
    private final IntText ms = new IntText(" мс");
    private boolean has;

    public PingHud() {
        super("hud_ping", "Ping", "Задержка до сервера.", 0.0, 0.28);
        labels[0] = "Пинг";
    }

    @Override
    protected void update(Minecraft mc) {
        has = false;
        if (mc.getConnection() == null || mc.player == null || mc.hasSingleplayerServer()) return;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) return;
        has = true;
        values[0] = ms.of(info.getLatency());
    }

    @Override
    protected boolean visibleNow() { return has; }
}
