package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.CustomFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom Fog: свой цвет тумана. Если методов нет — не применится. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @ModifyReturnValue(method = {"setupFog", "computeFogColor"}, at = @At("RETURN"), require = 0)
    private Vector4f mraz1visual$fogColor(Vector4f color) {
        return CustomFog.fog(color);
    }
}
