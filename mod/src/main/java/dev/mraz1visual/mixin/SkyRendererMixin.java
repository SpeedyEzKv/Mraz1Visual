package dev.mraz1visual.mixin;

import dev.mraz1visual.module.CustomFog;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Custom Fog: свой цвет купола неба. Если метода нет — не применится. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    //? if >=26.3 {
    /*// 26.3: цвет купола приходит вектором (0..1)
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true, require = 0)
    private org.joml.Vector3fc mraz1visual$sky(org.joml.Vector3fc c) {
        int argb = 0xFF000000 | Math.round(c.x() * 255) << 16 | Math.round(c.y() * 255) << 8 | Math.round(c.z() * 255);
        int r = CustomFog.sky(argb);
        if (r == argb) return c;
        return new org.joml.Vector3f((r >> 16 & 255) / 255f, (r >> 8 & 255) / 255f, (r & 255) / 255f);
    }
    *///?} else {
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true, require = 0)
    private int mraz1visual$sky(int color) {
        return CustomFog.sky(color);
    }
    //?}
}
