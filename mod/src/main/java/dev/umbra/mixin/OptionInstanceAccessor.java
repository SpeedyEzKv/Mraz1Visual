package dev.umbra.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Full Bright: записать яркость в обход проверки диапазона. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("value")
    T umbra$getValue();

    @Mutable
    @Accessor("value")
    void umbra$setValue(T value);
}
