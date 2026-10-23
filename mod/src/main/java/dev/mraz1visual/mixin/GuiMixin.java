package dev.mraz1visual.mixin;

import dev.mraz1visual.module.Cinematic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cinematic: в кинорежиме вместо HUD — киношные полосы (только картинка у тебя). */
// 26.2+: HUD рисует Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$cinematic(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (Cinematic.render(g)) ci.cancel();
    }
}
