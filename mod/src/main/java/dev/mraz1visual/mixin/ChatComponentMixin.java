package dev.mraz1visual.mixin;

import dev.mraz1visual.module.ChatTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat Tweaks: длинная история, чат без очистки, время и склейка повторов. Нет методов — не применится. */
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

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component mraz1visual$decorate(Component message) {
        return ChatTweaks.decorate(message, (ChatComponent) (Object) this);
    }
}
