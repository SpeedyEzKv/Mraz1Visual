package dev.umbra.mixin;

import dev.umbra.module.StreamerMode;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Streamer Mode: подмена ника при отрисовке любого текста. */
@Mixin(Font.class)
public abstract class FontMixin {
    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, require = 0)
    private String umbra$streamerString(String text) {
        return StreamerMode.filter(text);
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence umbra$streamerSeq(FormattedCharSequence text) {
        return StreamerMode.filter(text);
    }
}
