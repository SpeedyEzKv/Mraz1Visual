package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;

/** Занятая память Java: процент и МБ от максимума. Обновляется дважды в секунду. */
public final class MemoryHud extends TextHud {
    private long at;
    private int lastPct = -1;

    public MemoryHud() {
        super("hud_memory", "Memory Usage", "Сколько памяти занимает игра.", 0.0, 0.50);
        labels[0] = "Память";
    }

    @Override
    protected void update(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - at < 500) return;
        at = now;
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
        int pct = (int) (used * 100 / Math.max(1, max));
        if (pct == lastPct) return;
        lastPct = pct;
        values[0] = pct + "%  " + used + " / " + max + " МБ";
    }
}
