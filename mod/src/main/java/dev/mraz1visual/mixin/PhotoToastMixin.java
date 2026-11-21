package dev.mraz1visual.mixin;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Suppress drawing only; notification queues are not cleared. */
@Mixin(ToastManager.class)
public abstract class PhotoToastMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mraz1visual$photo(CallbackInfo ci){if(dev.mraz1visual.module.PhotoMode.active())ci.cancel();}
}
