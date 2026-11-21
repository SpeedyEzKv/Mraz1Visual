package dev.mraz1visual.mixin;

import dev.mraz1visual.module.PetReactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ItemPickupMixin {
    // Vanilla has scheduled the packet on the game thread, and the item still exists.
    @Inject(method="handleTakeItemEntity", at=@At(value="INVOKE",
            target="Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;getItemId()I", ordinal=0))
    private void mraz1visual$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null && mc.level!=null && packet.getPlayerId()==mc.player.getId() && packet.getAmount()>0
                && mc.level.getEntity(packet.getItemId()) instanceof ItemEntity item) PetReactions.pickup(item.getItem());
    }
}
