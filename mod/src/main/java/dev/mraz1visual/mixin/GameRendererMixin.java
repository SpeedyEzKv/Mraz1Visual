package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.AspectRatio;
import dev.mraz1visual.module.BetterMinecraft;
import dev.mraz1visual.module.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom, Aspect Ratio и тряска при уроне. Если метода нет — просто не применится. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"), require = 0)
    private float mraz1visual$zoom(float fov) {
        return Zoom.apply(fov);
    }

    @ModifyReturnValue(method = "getProjectionMatrix", at = @At("RETURN"), require = 0)
    private Matrix4f mraz1visual$aspect(Matrix4f m) {
        return AspectRatio.apply(m);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$noHurtCam(CallbackInfo ci) {
        if (BetterMinecraft.noHurtCam()) ci.cancel();
    }
}
