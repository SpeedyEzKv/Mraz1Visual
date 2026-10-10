package dev.mraz1visual.mixin;

import dev.mraz1visual.ui.menu.MenuRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Подменяет ванильное главное меню и список серверов на свои. Больше ничего. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen mraz1visual$route(Screen screen) {
        return MenuRouter.route((Minecraft) (Object) this, screen);
    }

    /** World Music: своя музыка вместо ситуационной. */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getSituationalMusic", at = @At("RETURN"), require = 0)
    private net.minecraft.sounds.Music mraz1visual$music(net.minecraft.sounds.Music original) {
        return dev.mraz1visual.module.WorldMusic.apply(original);
    }

    /** Заголовок окна: «Mraz1Visual 0.1.x» вместо «Minecraft* 1.21.11 — Одиночная игра». */
    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(dev.mraz1visual.WindowBranding.title());
    }
}
