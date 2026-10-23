package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: поле зрения считает GameRenderer (1.21.11) или камера (26.x). */
//? if >=26.1 {
/*@Mixin(net.minecraft.client.Camera.class)
*///?} else {
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
//?}
public abstract class FovMixin {
    //? if >=26.1 {
    /*@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"), require = 0)
    *///?} else {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"), require = 0)
    //?}
    private float mraz1visual$zoom(float fov) {
        return Zoom.apply(fov);
    }
}
