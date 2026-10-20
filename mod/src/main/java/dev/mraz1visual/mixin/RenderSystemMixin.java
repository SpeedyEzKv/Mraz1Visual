package dev.mraz1visual.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mraz1visual.module.FramePacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame Pacing: точный ограничитель кадров вместо ванильного ожидания событий окна. */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mraz1visual$pace(int fps, CallbackInfo ci) {
        if (FramePacing.pace(fps)) ci.cancel();
    }
}
