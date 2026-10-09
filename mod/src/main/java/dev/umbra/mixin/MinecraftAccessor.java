package dev.umbra.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void umbra$setUser(User user);

    @Accessor("rightClickDelay")
    int umbra$getRightClickDelay();

    @Accessor("rightClickDelay")
    void umbra$setRightClickDelay(int ticks);
}
