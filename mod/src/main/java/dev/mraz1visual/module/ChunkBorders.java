package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Границы чанка, в котором стоишь (как F3+G, только аккуратнее). */
public final class ChunkBorders extends Module implements WorldLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xC0B8B4F2));

    public ChunkBorders() {
        super("chunk_borders", "Chunk Borders", "Границы текущего чанка.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = (int) Math.floor(p.getX()) >> 4 << 4, cz = (int) Math.floor(p.getZ()) >> 4 << 4;
        double y0 = Math.floor(p.getY()) - 8, y1 = Math.floor(p.getY()) + 16;
        int c = color.get(), soft = Colors.mulAlpha(c, 0.35f);
        double w = 0.03;
        int[][] corners = {{cx, cz}, {cx + 16, cz}, {cx, cz + 16}, {cx + 16, cz + 16}};
        for (int[] k : corners) d.line(k[0], y0, k[1], k[0], y1, k[1], w * 2, c);
        for (double y = Math.ceil(y0 / 2) * 2; y <= y1; y += 2) {
            int cc = y == Math.floor(p.getY()) ? c : soft;
            d.line(cx, y, cz, cx + 16, y, cz, w, cc);
            d.line(cx, y, cz + 16, cx + 16, y, cz + 16, w, cc);
            d.line(cx, y, cz, cx, y, cz + 16, w, cc);
            d.line(cx + 16, y, cz, cx + 16, y, cz + 16, w, cc);
        }
        for (int i = 4; i < 16; i += 4) {
            d.line(cx + i, y0, cz, cx + i, y1, cz, w, soft);
            d.line(cx + i, y0, cz + 16, cx + i, y1, cz + 16, w, soft);
            d.line(cx, y0, cz + i, cx, y1, cz + i, w, soft);
            d.line(cx + 16, y0, cz + i, cx + 16, y1, cz + i, w, soft);
        }
    }
}
