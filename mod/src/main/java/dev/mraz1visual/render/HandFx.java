package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/**
 * Предмет в руке от 1-го лица: та же раскладка, что у ванильной модели «handheld» (меч, топор),
 * чтобы свои мечи и эффекты стояли ровно там, где был бы обычный меч.
 * После {@link #handheld} координаты — пиксели модели 0..1 (центр 0.5, 0.5, 0.5).
 */
public final class HandFx {
    private static final Quaternionf Q = new Quaternionf();

    private HandFx() {}

    /** Повторяет display.firstperson_righthand/lefthand из item/handheld.json и сдвиг модели на −0.5. */
    public static void handheld(PoseStack ps, boolean left) {
        float s = left ? -1f : 1f;
        ps.translate(s * 1.13f / 16f, 3.2f / 16f, 1.13f / 16f);
        Q.rotationXYZ(0f, (float) Math.toRadians(-90 * s), (float) Math.toRadians(25 * s));
        ps.mulPose(Q);
        ps.scale(0.68f, 0.68f, 0.68f);
        ps.translate(-0.5f, -0.5f, -0.5f);
    }

    // Диагональ меча в модели: рукоять внизу слева, остриё вверху справа (плоскость z = 0.5).
    public static final float BASE_X = 0.12f, BASE_Y = 0.12f, TIP_X = 0.96f, TIP_Y = 0.96f, Z = 0.5f;
}
