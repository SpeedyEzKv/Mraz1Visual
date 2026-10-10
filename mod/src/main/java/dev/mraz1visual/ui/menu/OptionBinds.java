package dev.mraz1visual.ui.menu;

import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.OptionInstance;

import java.util.function.DoubleFunction;

/**
 * Мост между настройками Minecraft (OptionInstance) и нашими компонентами.
 * Значение читается из игры при открытии экрана и пишется обратно при каждом изменении.
 * Ванильные колбэки (смена прорисовки, полноэкранный режим и т.п.) срабатывают как обычно.
 */
final class OptionBinds {
    private OptionBinds() {}

    interface Sync { void pull(); }

    static SliderSetting ints(String name, OptionInstance<Integer> o, int min, int max, int step, DoubleFunction<String> fmt,
                              String hint, java.util.List<Sync> syncs) {
        SliderSetting s = new SliderSetting(name, name, o.get(), min, max, step, "").format(fmt);
        s.onChange(() -> o.set((int) Math.round(s.get())));
        s.hint(hint);
        syncs.add(() -> s.setSilently(o.get()));
        return s;
    }

    static SliderSetting doubles(String name, OptionInstance<Double> o, double min, double max, double step, DoubleFunction<String> fmt,
                                 String hint, java.util.List<Sync> syncs) {
        SliderSetting s = new SliderSetting(name, name, o.get(), min, max, step, "").format(fmt);
        s.onChange(() -> o.set(s.get()));
        s.hint(hint);
        syncs.add(() -> s.setSilently(o.get()));
        return s;
    }

    static BoolSetting bool(String name, OptionInstance<Boolean> o, String hint, java.util.List<Sync> syncs) {
        BoolSetting s = new BoolSetting(name, name, o.get());
        s.onChange(() -> o.set(s.get()));
        s.hint(hint);
        syncs.add(() -> s.setSilently(o.get()));
        return s;
    }

    /** @param pairs пары: значение enum, подпись */
    static <E extends Enum<E>> EnumSetting choice(String name, OptionInstance<E> o, E[] values, String[] labels,
                                                  String hint, java.util.List<Sync> syncs) {
        String[] opts = new String[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            opts[i * 2] = values[i].name();
            opts[i * 2 + 1] = labels[i];
        }
        EnumSetting s = new EnumSetting(name, name, indexOf(values, o.get()), opts);
        s.onChange(() -> o.set(values[s.get()]));
        s.hint(hint);
        syncs.add(() -> s.setSilently(indexOf(values, o.get())));
        return s;
    }

    private static <E> int indexOf(E[] values, E v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    static String pct(double v) { return Math.round(v * 100) + "%"; }
}
