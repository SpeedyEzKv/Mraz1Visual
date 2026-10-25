package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * С оружием в руке ПКМ по сундуку, печке, двери или кнопке ничего не открывает — в бою не залипнешь в меню.
 * Клик просто не уходит дальше (как будто кликнул по воздуху). Присесть + ПКМ — как обычно.
 */
public final class NoInteract extends Module {
    private static NoInteract instance;
    final BoolSetting containers = add(new BoolSetting("containers", "Сундуки, печи и прочие меню", true));
    final BoolSetting doors = add(new BoolSetting("doors", "Двери, люки, калитки", true));
    final BoolSetting buttons = add(new BoolSetting("buttons", "Кнопки и рычаги", false));
    final BoolSetting sneakBypass = add(new BoolSetting("sneak_bypass", "Shift — открыть всё равно", true));

    public NoInteract() {
        super("no_interact", "No Interact", "С оружием в руке не открывает сундуки и двери.", Category.PVP);
        instance = this;
    }

    /** Вызывается из миксина MultiPlayerGameMode.useItemOn: true — клик по блоку не обрабатывать. */
    public static boolean blocks(LocalPlayer p, InteractionHand hand, BlockHitResult hit) {
        NoInteract m = instance;
        if (m == null || !m.isEnabled() || p == null || hit == null) return false;
        if (m.sneakBypass.get() && p.isShiftKeyDown()) return false;
        if (!weapon(p.getItemInHand(InteractionHand.MAIN_HAND))) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState st = p.level().getBlockState(pos);
        Block b = st.getBlock();
        if (m.doors.get() && (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock)) return true;
        if (m.buttons.get() && (b instanceof ButtonBlock || b instanceof LeverBlock)) return true;
        return m.containers.get() && (b instanceof EnderChestBlock || st.getMenuProvider(p.level(), pos) != null);
    }

    static boolean weapon(ItemStack s) {
        return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT));
    }
}
