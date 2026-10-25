package dev.mraz1visual.mixin;

import dev.mraz1visual.module.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: пока держишь клавишу зума, колесо мыши меняет приближение, а не слот хотбара. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (Zoom.scroll(yOffset)) ci.cancel();
    }
}
