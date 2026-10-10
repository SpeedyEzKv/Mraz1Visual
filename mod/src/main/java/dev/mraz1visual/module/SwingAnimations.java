package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Другой взмах рукой от первого лица. Меняет только картинку у тебя. */
public final class SwingAnimations extends Module {
    private static SwingAnimations instance;
    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0,
            "smooth", "Плавный", "down", "Сверху вниз", "spin", "Вращение", "push", "Толчок", "old", "Как в 1.7"));

    public SwingAnimations() {
        super("swing_animations", "Swing Animations", "Другой стиль взмаха рукой.", Category.VISUALS);
        instance = this;
    }

    /** Из миксина: true — взмах нарисован нами, ванильный не нужен. */
    public static boolean apply(PoseStack ps, HumanoidArm arm, float p) {
        SwingAnimations s = instance;
        if (s == null || !s.isEnabled()) return false;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(p * p * Mth.PI);
        float g = Mth.sin(Mth.sqrt(p) * Mth.PI);
        switch (s.style.getId()) {
            case "smooth" -> {
                ps.mulPose(Axis.YP.rotationDegrees(i * (45f - f * 8f)));
                ps.mulPose(Axis.ZP.rotationDegrees(i * g * -10f));
                ps.mulPose(Axis.XP.rotationDegrees(g * -35f));
                ps.mulPose(Axis.YP.rotationDegrees(i * -45f));
            }
            case "down" -> {
                ps.mulPose(Axis.YP.rotationDegrees(i * 45f));
                ps.mulPose(Axis.XP.rotationDegrees(g * -95f));
                ps.mulPose(Axis.YP.rotationDegrees(i * -45f));
            }
            case "spin" -> ps.mulPose(Axis.YP.rotationDegrees(i * p * -360f));
            case "push" -> ps.translate(0f, 0f, -0.35f * g);
            case "old" -> {
                ps.mulPose(Axis.YP.rotationDegrees(i * (45f + f * -20f)));
                ps.mulPose(Axis.ZP.rotationDegrees(i * g * -20f));
                ps.mulPose(Axis.XP.rotationDegrees(g * -80f));
                ps.mulPose(Axis.YP.rotationDegrees(i * -45f));
                ps.scale(0.85f, 0.85f, 0.85f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
