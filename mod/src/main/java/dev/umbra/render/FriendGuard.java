package dev.umbra.render;

import dev.umbra.core.Friends;
import dev.umbra.module.ChatHelpers;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/** No Friend Damage: удар по другу из friends.txt не отправляется. */
public final class FriendGuard {
    public static volatile boolean enabled;

    private FriendGuard() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (enabled && player == Minecraft.getInstance().player && entity instanceof Player p && Friends.is(p.getName().getString())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
