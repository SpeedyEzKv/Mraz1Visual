package dev.mraz1visual.ui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Живой фон меню: небо, две медленно вращающиеся короны, диск, мерцающие звёзды,
 * рябь на воде и лёгкий параллакс от мыши. Рисуется только в меню — в игре стоит ноль.
 * Все слои лежат в координатах исходной картинки 1920×1080, поэтому одна матрица на всё.
 */
final class EclipseBackground {
    private static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("mraz1visual", "textures/gui/menu/" + n + ".png");
    }

    private static final Identifier SKY = tex("sky"), CORONA_A = tex("corona_a"), CORONA_B = tex("corona_b"),
            SUN = tex("sun"), WATER = tex("water");

    static final int W = 1920, H = 1080;
    static final float CX = W * 0.66f, CY = H * 0.364f, R = H * 0.22f;
    private static final int HZ = 847;               // горизонт
    private static final int WATER_H = H - HZ;
    private static final int CORONA_TEX = 1024, SUN_TEX = 768;
    private static final int CORONA_A_D = Math.round(R * 3.4f * 2), CORONA_B_D = Math.round(R * 3.0f * 2);
    private static final int SUN_D = Math.round(R * 1.62f * 2);
    private static final int STRIP = 3;

    private static final int STARS = 70;
    private static final int[] starX = new int[STARS], starY = new int[STARS];
    private static final float[] starPhase = new float[STARS], starSpeed = new float[STARS];

    static {
        Random r = new Random(1337);
        for (int i = 0; i < STARS; i++) {
            starX[i] = r.nextInt(W);
            starY[i] = r.nextInt(HZ - 20);
            starPhase[i] = r.nextFloat() * 6.283f;
            starSpeed[i] = 0.6f + r.nextFloat() * 1.6f;
        }
    }

    private static final long START = System.nanoTime();
    private float parX, parY;

    /**
     * @param sunX, sunY где на экране (доли) держать центр затмения
     */
    void render(GuiGraphics g, int sw, int sh, float sunX, float sunY, float zoom, int mouseX, int mouseY, boolean animate) {
        float t = animate ? (System.nanoTime() - START) / 1e9f : 0f;
        g.fill(0, 0, sw, sh, 0xFF030308);

        float s = Math.max(sw / (float) W, sh / (float) H) * zoom;
        float dw = W * s, dh = H * s;
        float ox = Math.max(sw - dw, Math.min(0, sunX * sw - CX / W * dw));
        float oy = Math.max(sh - dh, Math.min(0, sunY * sh - CY / H * dh));

        // параллакс: солнце чуть «плывёт» за мышью
        float tx = animate ? (0.5f - mouseX / (float) Math.max(1, sw)) * 14f : 0f;
        float ty = animate ? (0.5f - mouseY / (float) Math.max(1, sh)) * 8f : 0f;
        parX += (tx - parX) * 0.06f;
        parY += (ty - parY) * 0.06f;

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox, oy);
        pose.scale(s, s);

        blit(g, SKY, 0, 0, W, H, 0, 0, W, H, W, H, 0xFFFFFFFF);

        // мерцающие звёзды
        for (int i = 0; i < STARS; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * starSpeed[i] + starPhase[i]);
            int alpha = (int) (a * a * 200);
            if (alpha < 8) continue;
            g.fill(starX[i], starY[i], starX[i] + 2, starY[i] + 2, (alpha << 24) | 0xDCDCFF);
        }

        float sx = CX + parX, sy = CY + parY;
        float pulse = 0.88f + 0.12f * (float) Math.sin(t * 0.7f);
        corona(g, CORONA_A, sx, sy, CORONA_A_D, t * 0.010f, alpha(pulse));
        corona(g, CORONA_B, sx, sy, CORONA_B_D, -t * 0.007f + 1.3f, alpha(0.75f + 0.25f * (float) Math.sin(t * 0.5f + 1.7f)));

        pose.pushMatrix();
        pose.translate(sx - SUN_D / 2f, sy - SUN_D / 2f);
        blit(g, SUN, 0, 0, SUN_D, SUN_D, 0, 0, SUN_TEX, SUN_TEX, SUN_TEX, SUN_TEX, 0xFFFFFFFF);
        pose.popMatrix();

        // вода: полоски с волновым сдвигом и мерцанием
        for (int i = 0, n = WATER_H / STRIP; i < n; i++) {
            float depth = i / (float) n;
            float wave = (float) Math.sin(t * 1.1f + i * 0.55f) * (2f + depth * 9f)
                    + (float) Math.sin(t * 0.37f + i * 0.13f) * 4f;
            float shimmer = 0.7f + 0.3f * (float) Math.sin(t * 2.3f + i * 1.7f);
            pose.pushMatrix();
            pose.translate(wave + parX * 0.3f, HZ + i * STRIP);
            blit(g, WATER, 0, 0, W, STRIP, 0, i * STRIP, W, STRIP, W, WATER_H, alpha(shimmer));
            pose.popMatrix();
        }
        pose.popMatrix();
    }

    private static int alpha(float a) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | 0xFFFFFF;
    }

    private static void corona(GuiGraphics g, Identifier id, float cx, float cy, int d, float angle, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(angle);
        pose.translate(-d / 2f, -d / 2f);
        blit(g, id, 0, 0, d, d, 0, 0, CORONA_TEX, CORONA_TEX, CORONA_TEX, CORONA_TEX, color);
        pose.popMatrix();
    }

    /** Единственный вызов blit в моде. Цвет ARGB умножается на текстуру (альфа — прозрачность слоя). */
    private static void blit(GuiGraphics g, Identifier id, int x, int y, int w, int h, float u, float v,
                             int regionW, int regionH, int texW, int texH, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH, color);
    }
}
