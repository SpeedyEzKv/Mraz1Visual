package dev.mraz1visual.mixin;

import dev.mraz1visual.module.SeeInvisible;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class InvisiblePlayerMixin {
    @Inject(method="isInvisibleTo",at=@At("HEAD"),cancellable=true)
    private void mraz$reveal(Player viewer,CallbackInfoReturnable<Boolean> ci){
        if(SeeInvisible.reveal((Entity)(Object)this))ci.setReturnValue(false);
    }
}
