package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Растянутое разрешение: картинка мира как при другом соотношении сторон (4:3, 5:4…). */
public final class AspectRatio extends Module {
    private static AspectRatio instance;
    final EnumSetting ratio = add(new EnumSetting("ratio", "Соотношение", 0, "4:3", "4:3", "5:4", "5:4", "16:10", "16:10", "1:1", "1:1", "21:9", "21:9"));

    public AspectRatio() {
        super("aspect_ratio", "Aspect Ratio", "Растянутая картинка мира, как на 4:3.", Category.VISUALS);
        instance = this;
    }

    /** Из миксина GameRenderer.getProjectionMatrix. */
    public static Matrix4f apply(Matrix4f m) {
        AspectRatio a = instance;
        if (a == null || !a.isEnabled() || m == null) return m;
        var win = Minecraft.getInstance().getWindow();
        if (win.getHeight() <= 0) return m;
        double screen = win.getWidth() / (double) win.getHeight();
        String[] p = a.ratio.getId().split(":");
        double target = Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
        return new Matrix4f(m).m00((float) (m.m00() * screen / target));
    }
}
