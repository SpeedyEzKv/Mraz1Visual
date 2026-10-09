package dev.umbra.core;

import net.minecraft.client.gui.GuiGraphics;

/** Модуль рисует в HUD. Вызывается только пока модуль включён. */
public interface HudLayer {
    void renderHud(GuiGraphics g, float partialTick);
}
