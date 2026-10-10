package dev.mraz1visual.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot mraz1visual$hoveredSlot();

    @Accessor("leftPos")
    int mraz1visual$leftPos();

    @Accessor("topPos")
    int mraz1visual$topPos();
}
