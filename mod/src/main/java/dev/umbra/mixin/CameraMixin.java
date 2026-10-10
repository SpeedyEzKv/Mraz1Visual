package dev.umbra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.umbra.module.FreeLook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Free Look: камера смотрит туда, куда крутит мышь, а не туда, куда смотрит игрок. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
    private void umbra$freeLook(Camera camera, float yRot, float xRot, Operation<Void> original) {
        if (FreeLook.active()) original.call(camera, FreeLook.yaw(), FreeLook.pitch());
        else original.call(camera, yRot, xRot);
    }
}
