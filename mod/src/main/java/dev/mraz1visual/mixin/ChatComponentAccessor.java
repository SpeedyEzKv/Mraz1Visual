package dev.mraz1visual.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Chat Tweaks: склейка повторов — убрать прошлую строку и перестроить чат. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> mraz1visual$allMessages();

    @Invoker("refreshTrimmedMessages")
    void mraz1visual$refreshTrimmedMessages();
}
