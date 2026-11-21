package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mraz1visual.module.FreeLook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Free Look: камера смотрит туда, куда крутит мышь, а не туда, куда смотрит игрок. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    //? if >=26.1 {
    /*@org.spongepowered.asm.mixin.injection.ModifyArg(method="setupPerspective",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"),index=3)
    private float mraz1visual$aspectWidth(float width){return dev.mraz1visual.module.AspectRatio.projectionWidth(width);}
    *///?}
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
    private void mraz1visual$freeLook(Camera camera, float yRot, float xRot, Operation<Void> original) {
        if (dev.mraz1visual.module.PhotoMode.active()) original.call(camera,yRot+dev.mraz1visual.module.PhotoMode.yaw(),net.minecraft.util.Mth.clamp(xRot+dev.mraz1visual.module.PhotoMode.pitch(),-85,85));
        else if (FreeLook.active()) original.call(camera, FreeLook.yaw(), FreeLook.pitch());
        else original.call(camera, yRot, xRot);
    }
}
