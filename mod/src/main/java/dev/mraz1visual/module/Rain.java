package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;

/** Звуки дождя и грозы отдельно: выключить или сделать тише/громче. Только громкость у тебя. */
public final class Rain extends Module {
    private static Rain instance;
    final BoolSetting rain = add(new BoolSetting("rain", "Звуки дождя", true));
    final SliderSetting rainVolume = add(new SliderSetting("rain_volume", "Громкость дождя", 100, 0, 200, 5, " %"));
    final BoolSetting thunder = add(new BoolSetting("thunder", "Звуки грозы", true));
    final SliderSetting thunderVolume = add(new SliderSetting("thunder_volume", "Громкость грозы", 100, 0, 200, 5, " %"));

    public Rain() {
        super("rain", "Rain", "Звуки дождя и грозы: вкл/выкл и громкость каждого.", Category.SKY);
        instance = this;
        markNew();
    }

    /** Множитель громкости по пути звука (из SoundInstanceMixin). */
    public static float volume(String path) {
        Rain r = instance;
        if (r == null || !r.isEnabled()) return 1f;
        if (path.startsWith("weather.rain")) return r.rain.get() ? r.rainVolume.getF() / 100f : 0f;
        if (path.startsWith("entity.lightning_bolt")) return r.thunder.get() ? r.thunderVolume.getF() / 100f : 0f;
        return 1f;
    }
}
