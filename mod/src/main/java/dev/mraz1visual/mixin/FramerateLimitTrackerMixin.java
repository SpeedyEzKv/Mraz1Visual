package dev.mraz1visual.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.mraz1visual.module.FramePacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Frame Pacing: свой предел FPS в меню (ванилла — 60) и ровный предел в игре. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Shadow
    public abstract FramerateLimitTracker.FramerateThrottleReason getThrottleReason();

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void mraz1visual$limit(CallbackInfoReturnable<Integer> cir) {
        int v = cir.getReturnValue();
        int r = FramePacing.limit(v, getThrottleReason());
        if (r != v) cir.setReturnValue(r);
    }
}
