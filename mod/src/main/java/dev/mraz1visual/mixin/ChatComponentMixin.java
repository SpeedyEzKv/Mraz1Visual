package dev.mraz1visual.mixin;

import dev.mraz1visual.module.ChatTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat Tweaks: длинная история и чат без очистки. Нет методов — не применится. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100), require = 0)
    private int mraz1visual$history(int vanilla) {
        return ChatTweaks.history(vanilla);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$keep(boolean clearHistory, CallbackInfo ci) {
        if (ChatTweaks.keep()) ci.cancel();
    }
}
