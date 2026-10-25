package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Своё время суток и ясная погода — только у тебя на экране. На игру и сервер не влияет. */
public final class Ambience extends Module {
    final BoolSetting time = add(new BoolSetting("time", "Своё время", true));
    final SliderSetting hour = add(new SliderSetting("hour", "Час", 18, 0, 23.5, 0.5, ":00")
            .format(v -> String.format("%02d:%02d", (int) v, (int) Math.round((v - (int) v) * 60))));
    final BoolSetting clear = add(new BoolSetting("clear", "Без дождя", true));
    final dev.mraz1visual.setting.EnumSetting weather = add(new dev.mraz1visual.setting.EnumSetting("weather", "Погода", 0,
            "legacy", "По настройке «Без дождя»", "clear", "Ясно", "rain", "Дождь / снег", "thunder", "Гроза"));

    public Ambience() {
        super("ambience", "Ambience", "Время суток и погода только для тебя.", Category.SKY);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) return;
        if (time.get()) {
            // 0 тиков = 6:00 утра
            long ticks = Math.round(((hour.get() - 6 + 24) % 24) * 1000);
            // 26.x: время идёт через «часы мира» (ClientClockManager) — своё время там пока не задаётся
            //? if <26.1
            mc.level.getLevelData().setDayTime(ticks);
        }
        String w = weather.getId();
        if (w.equals("clear") || w.equals("legacy") && clear.get()) {
            mc.level.setRainLevel(0f);
            mc.level.setThunderLevel(0f);
        } else if (w.equals("rain")) {
            mc.level.setRainLevel(1f);
            mc.level.setThunderLevel(0f);
        } else if (w.equals("thunder")) {
            mc.level.setRainLevel(1f);
            mc.level.setThunderLevel(1f);
        }
    }
}
