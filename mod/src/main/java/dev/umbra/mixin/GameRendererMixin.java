package dev.umbra.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.umbra.module.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: делит поле зрения. Если метода нет — просто не применится. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"), require = 0)
    private float umbra$zoom(float fov) {
        return Zoom.apply(fov);
    }
}
