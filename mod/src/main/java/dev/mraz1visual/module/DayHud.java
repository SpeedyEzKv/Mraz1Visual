package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;

/** Номер игрового дня. */
public final class DayHud extends TextHud {
    private final IntText day = new IntText("");

    public DayHud() {
        super("hud_day", "Day Counter", "Сколько игровых дней прошло.", 0.0, 0.70);
        labels[0] = "День";
    }

    @Override
    protected void update(Minecraft mc) {
        if (mc.level != null) values[0] = day.of((int) (dev.mraz1visual.compat.Mc.totalDayTime(mc.level) / 24000L) + 1);
    }
}
