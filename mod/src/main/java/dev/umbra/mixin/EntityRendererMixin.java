package dev.umbra.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.umbra.module.NameTags;
import dev.umbra.module.Optimization;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name Tags прячет ванильные ники игроков; Optimization не рисует далёкие сущности. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @ModifyReturnValue(method = "shouldShowName", at = @At("RETURN"), require = 0)
    private boolean umbra$nameTags(boolean show, @Local(argsOnly = true) Entity entity) {
        return show && !NameTags.hidesVanilla(entity);
    }

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"), require = 0)
    private boolean umbra$cull(boolean render, @Local(argsOnly = true) Entity entity) {
        return render && !Optimization.cull(entity);
    }
}
