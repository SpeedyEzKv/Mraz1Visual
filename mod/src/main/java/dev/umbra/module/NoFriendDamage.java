package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.render.FriendGuard;

/** Удар по другу (Misc → Friends) просто не отправляется. */
public final class NoFriendDamage extends Module {
    public NoFriendDamage() {
        super("no_friend_damage", "No Friend Damage", "Не даёт случайно ударить друга из списка Friends.", Category.PLAYER);
    }

    @Override
    protected void onEnable() { FriendGuard.enabled = true; }

    @Override
    protected void onDisable() { FriendGuard.enabled = false; }
}
