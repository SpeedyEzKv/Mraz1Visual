package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Запрет выбросить предмет (Q) из закреплённых слотов хотбара. Клавиша закрепляет текущий слот. */
public final class LockSlot extends Module implements HudLayer {
    private static LockSlot instance;
    final KeySetting key = add(new KeySetting("key", "Закрепить слот", InputConstants.KEY_L));
    private int mask;
    private boolean was;

    public LockSlot() {
        super("lock_slot", "Lock Slot", "Закреплённые слоты не выбросить клавишей Q.", Category.PLAYER);
        instance = this;
    }

    /** Из миксина LocalPlayer.drop: true — выброс запрещён. */
    public static boolean blocks() {
        LockSlot l = instance;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || !l.isEnabled() || mc.player == null) return false;
        return (l.mask >> mc.player.getInventory().getSelectedSlot() & 1) != 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = mc.player != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) {
            int s = mc.player.getInventory().getSelectedSlot();
            mask ^= 1 << s;
            boolean on = (mask >> s & 1) != 0;
            dev.mraz1visual.compat.Mc.message(mc.player, Component.literal(on ? "§7Mraz1Visual: слот §f" + (s + 1) + "§7 закреплён" : "§7Mraz1Visual: слот §f" + (s + 1) + "§7 откреплён"), true);
        }
        was = down;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (mask == 0) return;
        int x0 = g.guiWidth() / 2 - 91, y = g.guiHeight() - 22;
        for (int i = 0; i < 9; i++) {
            if ((mask >> i & 1) == 0) continue;
            int x = x0 + i * 20;
            g.fill(x + 3, y + 1, x + 19, y + 2, Theme.accent());
            g.fill(x + 15, y + 3, x + 19, y + 7, 0xE00C0C12);
            g.fill(x + 16, y + 4, x + 18, y + 6, Theme.accent());
        }
    }
}
