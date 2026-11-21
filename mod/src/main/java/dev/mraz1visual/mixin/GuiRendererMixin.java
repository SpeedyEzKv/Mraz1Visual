package dev.mraz1visual.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** RenderPearl rejects empty scissors; an empty intersection must produce no draw. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    //? if >=26.3 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "executeDraw", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void mraz$skipEmptyDraw(@org.spongepowered.asm.mixin.injection.Coerce Object draw,
            @org.spongepowered.asm.mixin.injection.Coerce Object pass,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var scissor = ((GuiDrawAccessor) draw).mraz$scissorArea();
        if (scissor == null) return;
        var window = net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        int scale = window.guiScale;
        int left = scissor.left()*scale, top = scissor.top()*scale;
        int right = Math.min(scissor.right()*scale,window.width);
        int bottom = Math.min(scissor.bottom()*scale,window.height);
        // A positive GUI rectangle can still become empty when clipped to the framebuffer.
        if (scissor.width() <= 0 || scissor.height() <= 0 || right <= left || bottom <= top) ci.cancel();
    }
    *///?}
}
