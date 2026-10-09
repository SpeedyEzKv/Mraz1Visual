package dev.umbra.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.umbra.module.Cape;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerInfo.class)
abstract class PlayerInfoMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"), require = 0)
    private PlayerSkin umbra$cape(PlayerSkin skin) {
        return Cape.apply((PlayerInfo) (Object) this, skin);
    }
}
