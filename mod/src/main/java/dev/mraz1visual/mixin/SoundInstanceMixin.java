package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.NoSounds;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No Sounds: громкость выбранных звуков — ноль. */
@Mixin(AbstractSoundInstance.class)
public abstract class SoundInstanceMixin {
    @ModifyReturnValue(method = "getVolume", at = @At("RETURN"), require = 0)
    private float mraz1visual$mute(float v) {
        return NoSounds.muted(((AbstractSoundInstance) (Object) this).getIdentifier().getPath()) ? 0f : v;
    }
}
