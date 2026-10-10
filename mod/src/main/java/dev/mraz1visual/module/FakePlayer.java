package dev.mraz1visual.module;

import com.mojang.authlib.GameProfile;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Манекен-копия тебя на месте включения. Есть только у тебя в клиенте —
 * сервер о нём не знает. Удобно настраивать Target ESP, Hit Effects, Name Tags.
 */
public final class FakePlayer extends Module {
    private static final int ID = -0x0BEEF;
    final BoolSetting copyInv = add(new BoolSetting("copy_inv", "Копировать броню и предметы", true));

    private RemotePlayer fake;

    public FakePlayer() {
        super("fake_player", "Fake Player", "Манекен-копия тебя, видна только тебе.", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        spawn(Minecraft.getInstance());
    }

    @Override
    protected void onDisable() {
        remove();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            fake = null;
            return;
        }
        if (fake == null || fake.level() != mc.level) spawn(mc);
    }

    private void spawn(Minecraft mc) {
        remove();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) return;
        RemotePlayer f = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), p.getName().getString()));
        f.setId(ID);
        f.copyPosition(p);
        f.setYHeadRot(p.getYHeadRot());
        f.setYBodyRot(p.getYRot());
        if (copyInv.get()) f.getInventory().replaceWith(p.getInventory());
        level.addEntity(f);
        fake = f;
    }

    private void remove() {
        Minecraft mc = Minecraft.getInstance();
        if (fake != null && mc.level != null && fake.level() == mc.level) mc.level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        fake = null;
    }
}
