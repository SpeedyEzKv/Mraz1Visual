package dev.mraz1visual.mixin;

import dev.mraz1visual.module.StreamerMode;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Streamer Mode: подмена ника при отрисовке любого текста. */
@Mixin(Font.class)
public abstract class FontMixin {
    @ModifyVariable(method = {"prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private boolean mraz$integrationShadow(boolean shadow) {
        return shadow && !(dev.mraz1visual.integration.IntegrationScreens.themed(net.minecraft.client.Minecraft.getInstance().screen)
                && dev.mraz1visual.integration.IntegrationTheme.light());
    }
    @ModifyVariable(method = {"prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int mraz$integrationTextColor(int color) {
        return dev.mraz1visual.integration.IntegrationScreens.themed(net.minecraft.client.Minecraft.getInstance().screen)
                ? dev.mraz1visual.integration.IntegrationTheme.nativeTextColor(color) : color;
    }
    @org.spongepowered.asm.mixin.Unique
    private static final net.minecraft.network.chat.FontDescription MRAZ_NATIVE = new net.minecraft.network.chat.FontDescription.Resource(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual", "integration_native"));

    @org.spongepowered.asm.mixin.Unique
    private static final net.minecraft.resources.Identifier MRAZ_DEFAULT = net.minecraft.resources.Identifier.withDefaultNamespace("default");

    @ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.network.chat.FontDescription mraz$integrationFont(net.minecraft.network.chat.FontDescription original) {
        if (dev.mraz1visual.core.Modules.client != null && !dev.mraz1visual.core.Modules.client.vanillaFont.get()
                && original instanceof net.minecraft.network.chat.FontDescription.Resource resource
                && resource.id().equals(MRAZ_DEFAULT)) {
            return MRAZ_NATIVE;
        }
        return original;
    }

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, require = 0)
    private String mraz1visual$streamerString(String text) {
        return StreamerMode.filter(text);
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence mraz1visual$streamerSeq(FormattedCharSequence text) {
        return StreamerMode.filter(text);
    }
}
