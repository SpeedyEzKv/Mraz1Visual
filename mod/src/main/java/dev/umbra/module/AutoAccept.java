package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Friends;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Принимает запрос на телепорт (/tpa) — от друзей или от всех. */
public final class AutoAccept extends Module {
    private static AutoAccept instance;
    final BoolSetting friendsOnly = add(new BoolSetting("friends_only", "Только от друзей", true));

    public AutoAccept() {
        super("auto_accept", "Auto Accept", "Сам принимает /tpa от друзей.", Category.MISC);
        instance = this;
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            AutoAccept a = instance;
            if (a == null || !a.isEnabled() || overlay) return;
            String text = message.getString();
            String low = text.toLowerCase(Locale.ROOT);
            if (!(low.contains("tpaccept") || low.contains("телепорт"))) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null || mc.player == null) return;
            for (String word : text.split("[^A-Za-z0-9_]+")) {
                if (word.length() < 3 || word.equalsIgnoreCase(mc.player.getName().getString())) continue;
                if (mc.getConnection().getPlayerInfo(word) == null) continue;
                if (a.friendsOnly.get() && !Friends.is(word)) continue;
                mc.getConnection().sendCommand("tpaccept " + word);
                return;
            }
        });
    }
}
