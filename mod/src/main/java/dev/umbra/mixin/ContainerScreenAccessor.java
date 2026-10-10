package dev.umbra.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot umbra$hoveredSlot();

    @Accessor("leftPos")
    int umbra$leftPos();

    @Accessor("topPos")
    int umbra$topPos();
}
