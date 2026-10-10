package dev.mraz1visual.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Full Bright: записать яркость в обход проверки диапазона. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("value")
    T mraz1visual$getValue();

    @Mutable
    @Accessor("value")
    void mraz1visual$setValue(T value);
}
