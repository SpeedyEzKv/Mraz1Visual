package dev.mraz1visual.mixin;

import dev.mraz1visual.module.LockSlot;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lock Slot: не выбрасывать предмет из закреплённого слота. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$lockSlot(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (LockSlot.blocks()) cir.setReturnValue(false);
    }
}
