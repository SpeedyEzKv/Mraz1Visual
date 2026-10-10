package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.mixin.ContainerScreenAccessor;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

/**
 * Помощник аукциона: в окне аукциона показывает цену за 1 штуку
 * и подсвечивает самый выгодный лот среди одинаковых предметов на странице.
 * Только читает описание предметов — ничего не покупает сам.
 */
public final class AuctionHelper extends Module {
    private static AuctionHelper instance;
    final BoolSetting perItem = add(new BoolSetting("per_item", "Цена за 1 шт. в подсказке", true));
    final BoolSetting cheapest = add(new BoolSetting("cheapest", "Подсвечивать самый дешёвый", true));
    final ColorSetting color = add(new ColorSetting("color", "Цвет подсветки", 0x6052E07A));

    public AuctionHelper() {
        super("auction_helper", "Auction Helper", "Цена за штуку и самый дешёвый лот на аукционе.", Category.MISC);
        instance = this;
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            AuctionHelper a = instance;
            if (a == null || !a.isEnabled() || !a.perItem.get() || !inAuction() || stack.getCount() <= 1) return;
            long price = price(stack);
            if (price > 0) lines.add(Component.literal("§7За 1 шт.: §a" + format(price / (double) stack.getCount())));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                AuctionHelper a = instance;
                if (a == null || !a.isEnabled() || !a.cheapest.get() || !inAuction()) return;
                ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
                Slot best = null;
                double bestPer = Double.MAX_VALUE;
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.container == client.player.getInventory()) continue;
                    ItemStack st = slot.getItem();
                    if (st.isEmpty()) continue;
                    long p = price(st);
                    if (p <= 0) continue;
                    double per = p / (double) st.getCount();
                    if (per < bestPer) {
                        bestPer = per;
                        best = slot;
                    }
                }
                if (best != null) {
                    int x = acc.umbra$leftPos() + best.x, y = acc.umbra$topPos() + best.y;
                    g.fill(x, y, x + 16, y + 16, a.color.get());
                }
            });
        });
    }

    private static boolean inAuction() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) return false;
        String t = s.getTitle().getString().toLowerCase(Locale.ROOT);
        return t.contains("аукцион") || t.contains("auction") || t.contains("рынок") || t.contains("market");
    }

    /** Ищет в описании строку с ценой и вынимает число: «Цена: 1,250,000» → 1250000. */
    static long price(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            String s = line.getString();
            String low = s.toLowerCase(Locale.ROOT);
            if (!(low.contains("цен") || low.contains("price") || low.contains("стоим") || low.contains("$") || low.contains("¤"))) continue;
            long v = 0;
            boolean any = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    v = v * 10 + (c - '0');
                    any = true;
                } else if (any && (c == ',' || c == ' ' || c == '.' || c == ' ' || c == '_')) {
                    // разделитель разрядов
                } else if (any) break;
            }
            if (any && v > 0) return v;
        }
        return -1;
    }

    private static String format(double v) {
        if (v >= 100) return String.format(Locale.ROOT, "%,d", Math.round(v)).replace(',', ' ');
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
