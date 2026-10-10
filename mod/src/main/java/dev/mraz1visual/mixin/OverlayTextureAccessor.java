package dev.mraz1visual.mixin;

import net.minecraft.client.renderer.OverlayTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit Color: текстура вспышки урона. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
    @Accessor("texture")
    DynamicTexture mraz1visual$texture();
}
