package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;

/** Кадры в секунду. */
public final class FpsHud extends TextHud {
    private final IntText fps = new IntText("");

    public FpsHud() {
        super("hud_fps", "FPS", "Кадры в секунду.", 0.0, 0.10);
        labels[0] = "FPS";
    }

    @Override
    protected void update(Minecraft mc) {
        values[0] = fps.of(mc.getFps());
    }
}
