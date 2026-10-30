package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.mixin.ContainerScreenAccessor;
import dev.mraz1visual.setting.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;

/**
 * Быстрое перекладывание в сундуках и инвентаре:
 * Shift + ЛКМ, проведённая по слотам, — переносит каждый слот;
 * колесо над слотом — переносит стак (как Shift+клик).
 */
public final class ItemScroller extends Module {
    private static ItemScroller instance;
    final BoolSetting drag = add(new BoolSetting("drag", "Зажать клавишу и провести ЛКМ по слотам", true));
    final BoolSetting wheel = add(new BoolSetting("wheel", "Колесо над слотом — перенести стак", true));
    final BoolSetting wheelShift = add(new BoolSetting("wheel_shift", "Колесо только с Shift", false));
    final dev.mraz1visual.setting.KeySetting dragKey = add(new dev.mraz1visual.setting.KeySetting("drag_key", "Клавиша протягивания", InputConstants.KEY_RSHIFT));
    final dev.mraz1visual.setting.SliderSetting delay = add(new dev.mraz1visual.setting.SliderSetting("delay", "Задержка между кликами", 50, 0, 300, 5, " мс")
            .hint("Без задержки сервер может кикнуть за спам кликами."));

    /** Слоты, по которым провели, — переносятся по очереди с задержкой. */
    private final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
    private long lastClick;

    private final Set<Integer> visited = new HashSet<>();

    public ItemScroller() {
        super("item_scroller", "Item Scroller", "RShift + провести мышью по слотам — быстрый перенос; задержка против кика.", Category.MISC);
        markNew();
        instance = this;
    }

    private static boolean shift() {
        return Keys.isDown(InputConstants.KEY_LSHIFT) || Keys.isDown(InputConstants.KEY_RSHIFT);
    }

    private static void quickMove(Minecraft mc, AbstractContainerScreen<?> cs, Slot slot) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(cs.getMenu().containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static boolean supported(Object screen) {
        return screen instanceof AbstractContainerScreen<?> && !(screen instanceof CreativeModeInventoryScreen);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!supported(screen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, horizontal, vertical) -> {
                ItemScroller is = instance;
                if (is == null || !is.isEnabled() || !is.wheel.get()) return true;
                if (is.wheelShift.get() && !shift()) return true;
                if (!cs.getMenu().getCarried().isEmpty()) return true;
                Slot slot = ((ContainerScreenAccessor) cs).mraz1visual$hoveredSlot();
                if (slot == null || !slot.hasItem()) return true;
                quickMove(client, cs, slot);
                return false;
            });
            // протягивание проверяем каждый кадр, а не раз в тик — иначе быстрые движения пропускают слоты
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                ItemScroller is = instance;
                if (is != null && is.isEnabled()) is.dragFrame(client, cs);
            });
        });
    }

    private void dragFrame(Minecraft mc, AbstractContainerScreen<?> cs) {
        // очередь отрабатывает и после отпускания мыши — с задержкой, чтобы не кикнуло
        long now = System.currentTimeMillis();
        if (!queue.isEmpty() && now - lastClick >= delay.get()) {
            int idx = queue.poll();
            if (idx >= 0 && idx < cs.getMenu().slots.size() && cs.getMenu().getCarried().isEmpty()) {
                Slot s = cs.getMenu().slots.get(idx);
                if (s.hasItem()) quickMove(mc, cs, s);
            }
            lastClick = now;
        }
        boolean held = Keys.isDown(dragKey.get()) || shift() && dragKey.get() == InputConstants.KEY_RSHIFT;
        if (!drag.get() || mc.screen != cs || !held || !mc.mouseHandler.isLeftPressed()) {
            visited.clear();
            return;
        }
        if (!cs.getMenu().getCarried().isEmpty()) return;
        Slot slot = ((ContainerScreenAccessor) cs).mraz1visual$hoveredSlot();
        if (slot == null || !visited.add(slot.index)) return;
        if (slot.hasItem()) queue.add(slot.index);
    }
}
