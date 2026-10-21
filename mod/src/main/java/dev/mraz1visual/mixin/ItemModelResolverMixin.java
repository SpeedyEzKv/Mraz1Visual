package dev.mraz1visual.mixin;

import dev.mraz1visual.render.ItemFx;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Свои мечи и аура: пометить предмет моего игрока при сборке его состояния (только флаг, ничего не меняет). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("RETURN"), require = 0)
    private void mraz1visual$tag(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        ItemFx.tag(state, stack, ctx, owner);
    }
}
