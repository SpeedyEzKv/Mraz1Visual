package dev.umbra.mixin;

import dev.umbra.ui.menu.MenuRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Подменяет ванильное главное меню и список серверов на свои. Больше ничего. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen umbra$route(Screen screen) {
        return MenuRouter.route((Minecraft) (Object) this, screen);
    }
}
