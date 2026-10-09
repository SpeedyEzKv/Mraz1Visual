package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Своё время суток и ясная погода — только у тебя на экране. На игру и сервер не влияет. */
public final class Ambience extends Module {
    final BoolSetting time = add(new BoolSetting("time", "Своё время", true));
    final SliderSetting hour = add(new SliderSetting("hour", "Час", 18, 0, 23.5, 0.5, ":00")
            .format(v -> String.format("%02d:%02d", (int) v, (int) Math.round((v - (int) v) * 60))));
    final BoolSetting clear = add(new BoolSetting("clear", "Без дождя", true));

    public Ambience() {
        super("ambience", "Ambience", "Время суток и погода только для тебя.", Category.WORLD);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) return;
        if (time.get()) {
            // 0 тиков = 6:00 утра
            long ticks = Math.round(((hour.get() - 6 + 24) % 24) * 1000);
            mc.level.getLevelData().setDayTime(ticks);
        }
        if (clear.get()) {
            mc.level.setRainLevel(0f);
            mc.level.setThunderLevel(0f);
        }
    }
}
