package dev.mraz1visual.mixin;

import dev.mraz1visual.module.ThirdPersonCam;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Third Person: расстояние камеры. Проверка стен (getMaxZoom) остаётся ванильной. */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0, require = 0)
    private float mraz1visual$distance(float d) {
        return ThirdPersonCam.distance(d);
    }
}
