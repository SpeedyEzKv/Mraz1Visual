package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Нимб над головой: светящееся кольцо, видно от третьего лица и в зеркале F5. Только у тебя. */
public final class Halo extends Module implements WorldLayer {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ring", "Кольцо", "orbit", "Кольцо с огоньками", "double", "Двойное"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFF6E7A8));
    final BoolSetting rainbow = add(new BoolSetting("rainbow", "Переливается", false));

    public Halo() {
        super("halo", "Halo", "Светящийся нимб над головой.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible()) return;
        double x = p.xo + (p.getX() - p.xo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
        double y = p.yo + (p.getY() - p.yo) * pt + p.getBbHeight() + 0.28 + Math.sin(System.currentTimeMillis() / 600.0) * 0.03;
        if (p.isCrouching()) y -= 0.3;
        float time = System.currentTimeMillis() / 1000f;
        int c = rainbow.get() ? 0xFF000000 | Colors.hsv(time * 0.15f % 1f, 0.45f, 1f) : color.get();
        d.ring(x, y, z, 0.26, 0.36, 48, Colors.mulAlpha(c, 0f), Colors.mulAlpha(c, 0.25f)); // свечение
        d.ring(x, y, z, 0.3, 0.33, 48, Colors.mulAlpha(c, 0.95f), Colors.mulAlpha(c, 0.95f));
        if (style.getId().equals("double")) d.ring(x, y + 0.06, z, 0.2, 0.22, 40, Colors.mulAlpha(c, 0.7f), Colors.mulAlpha(c, 0.7f));
        if (style.getId().equals("orbit")) {
            for (int i = 0; i < 3; i++) {
                double a = time * 2.2 + i * Math.PI * 2 / 3;
                d.billboardDot(x + Math.cos(a) * 0.315, y + 0.01, z + Math.sin(a) * 0.315, 0.06, Colors.mulAlpha(0xFFFFFFFF, 0.95f));
            }
        }
    }
}
