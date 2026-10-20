package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Вершины эффектов в формате сущностей (белая текстура, полный свет). Эффекты у предметов и модели игрока
 * рисуются теми же типами рендера, что и сами предметы, — поэтому попадают в тот же проход и ту же проекцию
 * (в руке, от 3-го лица, в превью) и дружат с Iris. Твёрдые части — с ванильным освещением по нормали,
 * свечение — «эмиссивный» тип без освещения.
 */
public final class FxVertex {
    private FxVertex() {}

    /** Твёрдые части (клинок, рукоять): полупрозрачный тип сущностей, освещение по нормали. */
    public static RenderType solid() {
        return RenderTypes.entityTranslucent(SkyTextures.white());
    }

    /**
     * Свечение: эмиссивный тип (как глаза паука) — без освещения, в том же проходе, что и предмет.
     * Аддитивные типы (молния, заряженный крипер) у руки от 1-го лица рисуются в другом проходе и «съезжают»,
     * поэтому мягкое свечение делается полупрозрачным с подсветкой к белому (см. {@link #putGlow}).
     */
    public static RenderType glow() {
        return RenderTypes.eyes(SkyTextures.white());
    }

    /** Вершина свечения: прозрачность ×2 и лёгкий сдвиг к белому — на тёмном фоне читается как свет, а не краска. */
    public static void putGlow(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        int a = Math.min(255, (argb >>> 24) * 2);
        float w = a / 255f * 0.25f;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        r = Math.round(r + (255 - r) * w); g = Math.round(g + (255 - g) * w); b = Math.round(b + (255 - b) * w);
        put(vc, m, x, y, z, a << 24 | r << 16 | g << 8 | b, 0f, 0f, 1f);
    }

    public static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, int color, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(color).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
    }

    public static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, int color) {
        put(vc, m, x, y, z, color, 0f, 0f, 1f);
    }
}
