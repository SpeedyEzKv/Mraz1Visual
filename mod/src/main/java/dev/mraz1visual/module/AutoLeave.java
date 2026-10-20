package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Friends;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Выход с сервера (или /hub, /spawn), когда мало здоровья или рядом чужой игрок.
 * Друзья и NPC без строки в табе не считаются.
 */
public final class AutoLeave extends Module {
    final BoolSetting onHealth = add(new BoolSetting("on_health", "При низком здоровье", true));
    final SliderSetting health = add(new SliderSetting("health", "Порог здоровья", 6, 1, 19, 1, " HP"));
    final BoolSetting onPlayer = add(new BoolSetting("on_player", "Когда рядом игрок", false));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 30, 5, 128, 1, " бл."));
    final BoolSetting ignoreFriends = add(new BoolSetting("ignore_friends", "Не считать друзей", true));
    final EnumSetting action = add(new EnumSetting("action", "Действие", 0,
        "disconnect", "Выйти с сервера", "hub", "/hub", "spawn", "/spawn"));
    final BoolSetting disableAfter = add(new BoolSetting("disable_after", "Выключиться после срабатывания", true));

    /** Когда последний раз сработал — Auto Reconnect не переподключается после этого. */
    static long lastLeaveAt;

    public AutoLeave() {
        super("auto_leave", "Auto Leave", "Выходит с сервера, когда мало HP или рядом чужой игрок.", Category.PLAYER);
    }

    public static boolean leftRecently() {
        return System.currentTimeMillis() - lastLeaveAt < 60_000;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.getConnection() == null || !p.isAlive()) return;

        String reason = null;
        if (onHealth.get() && p.getHealth() <= health.get()) {
            reason = "мало здоровья (" + Math.round(p.getHealth()) + " HP)";
        } else if (onPlayer.get()) {
            double r = radius.get();
            for (Player o : mc.level.players()) {
                if (o == p || !o.isAlive()) continue;
                if (mc.getConnection().getPlayerInfo(o.getUUID()) == null) continue; // NPC / фейк
                String name = o.getName().getString();
                if (ignoreFriends.get() && Friends.is(name)) continue;
                if (p.distanceTo(o) <= r) { reason = "рядом " + name; break; }
            }
        }
        if (reason == null) return;

        lastLeaveAt = System.currentTimeMillis();
        switch (action.getId()) {
            case "hub" -> mc.getConnection().sendCommand("hub");
            case "spawn" -> mc.getConnection().sendCommand("spawn");
            default -> mc.getConnection().getConnection().disconnect(Component.literal("Auto Leave: " + reason));
        }
        if (disableAfter.get()) setEnabled(false);
    }
}
