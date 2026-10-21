package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** При смене слота хотбара — мягкий световой импульс вокруг выбранной ячейки (расходится и тает). */
public final class SlotPulse extends Module implements HudLayer {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 6, Palette.OPTIONS));

    private int lastSlot = -1;
    private float t = 1;
    private long last;

    public SlotPulse() {
        super("slot_pulse", "Slot Pulse", "Мягкий световой импульс вокруг слота хотбара при переключении.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        int slot = mc.player.getInventory().getSelectedSlot();
        if (slot != lastSlot) {
            if (lastSlot >= 0) t = 0;
            lastSlot = slot;
        }
        if (t >= 1) return;
        t = Math.min(1, t + dt / 0.45f);
        float e = Ease.outCubic(t), a = (float) Math.pow(1 - t, 1.5) * Fx.brightness();
        // ячейка ванильного хотбара: 20 px по горизонтали от центра экрана
        float cx = g.guiWidth() / 2f - 91 + slot * 20 + 11, cy = g.guiHeight() - 11;
        float s = 22 + e * 10;
        int c = Fx.color(preset.getId(), 0.25f, Fx.time(), 0xFFFFFFFF, 0xFFFFFFFF);
        Sdf.shape(g, cx - s / 2, cy - s / 2, s, s, 6 + e * 3, Sdf.SHADOW, 6, Colors.mulAlpha(c, 0.45f * a), Colors.mulAlpha(c, 0.45f * a));
        Sdf.shape(g, cx - s / 2, cy - s / 2, s, s, 6 + e * 3, Sdf.RING, 1.2f, Colors.mulAlpha(c, 0.9f * a), Colors.mulAlpha(c, 0.6f * a));
    }
}
