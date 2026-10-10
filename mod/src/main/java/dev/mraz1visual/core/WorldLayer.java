package dev.mraz1visual.core;

import dev.mraz1visual.render.WorldDraw;

/** Модуль рисует в мире (с проверкой глубины — сквозь стены ничего не видно). */
public interface WorldLayer {
    void renderWorld(WorldDraw draw, float partialTick);
}
