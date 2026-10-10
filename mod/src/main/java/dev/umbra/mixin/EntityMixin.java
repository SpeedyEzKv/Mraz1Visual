package dev.umbra.mixin;

import dev.umbra.module.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Look: пока клавиша зажата, мышь крутит камеру, а не игрока. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
    private void umbra$freeLook(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && FreeLook.turn(yRot, xRot)) ci.cancel();
    }
}
