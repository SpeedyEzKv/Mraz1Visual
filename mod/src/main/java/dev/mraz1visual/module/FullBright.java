package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.mixin.OptionInstanceAccessor;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Яркость выше ванильного максимума — в пещерах и ночью светло. Выключил — яркость вернулась. */
public final class FullBright extends Module {
    final SliderSetting level = add(new SliderSetting("level", "Яркость", 10, 1, 15, 0.5, "×"));
    private Double saved;

    public FullBright() {
        super("full_bright", "Full Bright", "Всё видно и в темноте, без факелов.", Category.VISUALS);
    }

    @SuppressWarnings("unchecked")
    private static OptionInstanceAccessor<Double> gamma() {
        return (OptionInstanceAccessor<Double>) (Object) Minecraft.getInstance().options.gamma();
    }

    @Override
    public void onTick(Minecraft mc) {
        OptionInstanceAccessor<Double> g = gamma();
        double want = level.get();
        Double cur = g.mraz1visual$getValue();
        if (saved == null) saved = cur != null && cur <= 1.0 ? cur : 0.5;
        if (cur == null || Math.abs(cur - want) > 1e-6) g.mraz1visual$setValue(want);
    }

    @Override
    protected void onDisable() {
        if (saved != null) gamma().mraz1visual$setValue(saved);
        saved = null;
    }
}
