package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.CustomFog;
import dev.mraz1visual.module.CustomSky;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom Fog: свой цвет тумана. Custom Sky: туман подстраивается под горизонт живого неба — без шва
 * на краю прорисовки. Только computeFogColor: setupFog возвращает его же результат, иначе цвет смешивался бы дважды.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"), require = 0)
    private Vector4f mraz1visual$fogColor(Vector4f color) {
        return CustomSky.fog(CustomFog.fog(color));
    }
}
