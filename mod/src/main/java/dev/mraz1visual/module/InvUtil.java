package dev.mraz1visual.module;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

/** Общие мелочи для клавиш инвентаря. */
final class InvUtil {
    private InvUtil() {}

    /** Слот инвентаря (0–35) → номер слота в меню инвентаря игрока. */
    static int menuSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    static int find(LocalPlayer p, Item item, int from, int to) {
        for (int i = from; i < to; i++) if (p.getInventory().getItem(i).getItem() == item) return i;
        return -1;
    }
}
