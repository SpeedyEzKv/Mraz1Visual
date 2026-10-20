package dev.mraz1visual.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Custom Sky: обои в небе с линейной фильтрацией (сглаживание при растяжении). */
@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Mutable
    @Accessor("sampler")
    void mraz1visual$setSampler(GpuSampler sampler);
}
