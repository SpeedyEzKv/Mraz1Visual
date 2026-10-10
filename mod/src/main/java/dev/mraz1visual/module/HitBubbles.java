package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Пузыри-кольца, расходящиеся от точки удара. */
public final class HitBubbles extends Module implements AttackListener, WorldLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFB8B4F2));
    final SliderSetting count = add(new SliderSetting("count", "Количество", 3, 1, 6, 1, ""));

    private final List<double[]> bubbles = new ArrayList<>(); // x y z start size
    private final Random rnd = new Random();

    public HitBubbles() {
        super("hit_bubbles", "Hit Bubbles", "Расходящиеся кольца на месте удара.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // точка на цели со стороны игрока
        double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.6, cz = e.getZ();
        double dx = mc.player.getX() - cx, dz = mc.player.getZ() - cz, len = Math.max(0.001, Math.hypot(dx, dz));
        cx += dx / len * e.getBbWidth() * 0.55;
        cz += dz / len * e.getBbWidth() * 0.55;
        long now = System.currentTimeMillis();
        for (int i = 0; i < count.getI(); i++) {
            bubbles.add(new double[]{cx + (rnd.nextDouble() - 0.5) * 0.5, cy + (rnd.nextDouble() - 0.5) * 0.6, cz + (rnd.nextDouble() - 0.5) * 0.5,
                    now + i * 60L, 0.25 + rnd.nextDouble() * 0.3});
        }
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        long now = System.currentTimeMillis();
        bubbles.removeIf(b -> now - b[3] > 700);
        int c = color.get();
        for (double[] b : bubbles) {
            if (now < b[3]) continue;
            float t = (float) ((now - b[3]) / 700.0);
            float ease = 1f - (1f - t) * (1f - t);
            float a = 1f - t;
            d.billboardRing(b[0], b[1] + t * 0.2, b[2], b[4] * (0.3 + ease), 0.05 * (1 - t) + 0.015, 32, Colors.mulAlpha(c, a));
            d.billboardDot(b[0], b[1] + t * 0.2, b[2], b[4] * 0.5 * (1 - t), Colors.mulAlpha(c, a * 0.35f));
        }
    }
}
