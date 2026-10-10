package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Friends;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Друзья: наведи прицел на игрока и нажми колёсико мыши — добавить или убрать.
 * Список лежит в config/umbra/friends.txt. Друзей не бьёт No Friend Damage,
 * они подсвечиваются своим цветом в Name Tags и ESP.
 */
public final class FriendsModule extends Module {
    private static FriendsModule instance;
    final BoolSetting middleClick = add(new BoolSetting("middle_click", "Колёсико мыши — добавить/убрать", true));
    private boolean was;

    public FriendsModule() {
        super("friends", "Friends", "Колёсико мыши по игроку — добавить в друзья или убрать.", Category.MISC);
        instance = this;
        setEnabledDefault();
    }

    private void setEnabledDefault() {
        loadEnabled(true);
    }

    /** Подсвечивать ли друга (модуль включён). */
    public static boolean highlight(String name) {
        return instance != null && instance.isEnabled() && Friends.is(name);
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = middleClick.get() && mc.screen == null && mc.options.keyPickItem.isDown();
        if (down && !was && mc.player != null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
            String name = p.getName().getString();
            boolean now = Friends.toggle(name);
            mc.player.displayClientMessage(Component.literal(now ? "§7Umbra: §a" + name + "§7 теперь в друзьях" : "§7Umbra: §f" + name + "§7 убран из друзей"), true);
        }
        was = down;
    }
}
