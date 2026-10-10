package dev.mraz1visual.mixin;

import dev.mraz1visual.module.CustomFog;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Custom Fog: свой цвет купола неба. Если метода нет — не применится. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true, require = 0)
    private int mraz1visual$sky(int color) {
        return CustomFog.sky(color);
    }
}
