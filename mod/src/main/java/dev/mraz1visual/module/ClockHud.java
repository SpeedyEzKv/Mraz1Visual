package dev.mraz1visual.module;

import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;

import java.time.LocalTime;

/** Часы компьютера. Строка обновляется раз в секунду. */
public final class ClockHud extends TextHud {
    final BoolSetting h12 = add(new BoolSetting("h12", "12-часовой формат", false));
    final BoolSetting seconds = add(new BoolSetting("seconds", "Секунды", false));
    private long lastSec = -1;
    private boolean lastH12, lastSeconds;

    public ClockHud() {
        super("hud_clock", "Clock", "Текущее время.", 0.0, 0.40);
        labels[0] = "Время";
    }

    @Override
    protected void update(Minecraft mc) {
        long sec = System.currentTimeMillis() / 1000;
        if (sec == lastSec && h12.get() == lastH12 && seconds.get() == lastSeconds) return;
        lastSec = sec;
        lastH12 = h12.get();
        lastSeconds = seconds.get();
        LocalTime t = LocalTime.now();
        int h = t.getHour();
        String suffix = "";
        if (lastH12) {
            suffix = h < 12 ? " AM" : " PM";
            h = h % 12 == 0 ? 12 : h % 12;
        }
        StringBuilder sb = new StringBuilder(12);
        sb.append(h).append(':').append(t.getMinute() < 10 ? "0" : "").append(t.getMinute());
        if (lastSeconds) sb.append(':').append(t.getSecond() < 10 ? "0" : "").append(t.getSecond());
        String s = sb.append(suffix).toString();
        if (!s.equals(values[0])) values[0] = s;
    }
}
