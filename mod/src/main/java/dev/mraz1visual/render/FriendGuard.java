package dev.mraz1visual.render;

import dev.mraz1visual.core.Friends;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/** No Friend Damage: удар по другу не отправляется. */
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
