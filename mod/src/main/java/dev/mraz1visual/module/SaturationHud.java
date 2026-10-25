package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;

/** Насыщение (скрытая шкала еды) с точностью до десятых. */
public final class SaturationHud extends TextHud {
    private int last = -1;

    public SaturationHud() {
        super("hud_saturation", "Saturation", "Скрытое насыщение — пока оно есть, голод не тратится.", 1.0, 0.54);
        labels[0] = "Насыщение";
    }

    @Override
    protected void update(Minecraft mc) {
        if (mc.player == null) return;
        int v = Math.round(mc.player.getFoodData().getSaturationLevel() * 10);
        if (v == last) return;
        last = v;
        values[0] = v / 10 + "." + v % 10;
    }
}
