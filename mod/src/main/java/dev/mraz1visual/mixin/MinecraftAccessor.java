package dev.mraz1visual.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void mraz1visual$setUser(User user);

    @Accessor("rightClickDelay")
    int mraz1visual$getRightClickDelay();

    @Accessor("rightClickDelay")
    void mraz1visual$setRightClickDelay(int ticks);
}
