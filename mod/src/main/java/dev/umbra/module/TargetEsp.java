package dev.umbra.module;

import dev.umbra.core.AttackListener;
import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Эффект вокруг цели, которую ты бьёшь: кольцо плавает вверх-вниз по телу.
 * С проверкой глубины — за стеной цель не подсвечивается.
 */
public final class TargetEsp extends Module implements WorldLayer, AttackListener {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ring", "Кольцо", "double", "Два кольца", "base", "Круг у ног"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xD0B8B4F2));
    final SliderSetting keep = add(new SliderSetting("keep", "Держать после удара", 3, 1, 10, 0.5, " с"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.3, 3, 0.1, "×"));

    private LivingEntity target;
    private long hitAt;
    private static final long START = System.nanoTime();

    public TargetEsp() {
        super("target_esp", "Target ESP", "Эффект вокруг цели после твоего удара.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            target = le;
            hitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        LivingEntity t = target;
        if (t == null || t.isRemoved() || t.isDeadOrDying() || System.currentTimeMillis() - hitAt > keep.get() * 1000) {
            target = null;
            return;
        }
        Vec3 p = t.getPosition(pt);
        float time = (System.nanoTime() - START) / 1e9f * speed.getF();
        double h = t.getBbHeight(), r = t.getBbWidth() * 0.85 + 0.15;
        int c = color.get();
        int soft = Colors.mulAlpha(c, 0.1f);
        switch (style.getId()) {
            case "base" -> d.ring(p.x, p.y + 0.02, p.z, r * 0.7, r * 1.1, 48, soft, c);
            case "double" -> {
                double y1 = p.y + h * (0.5 + 0.45 * Math.sin(time * 2.2));
                double y2 = p.y + h * (0.5 - 0.45 * Math.sin(time * 2.2));
                d.ring(p.x, y1, p.z, r * 0.92, r, 48, soft, c);
                d.ring(p.x, y2, p.z, r * 0.92, r, 48, soft, c);
            }
            default -> {
                double y = p.y + h * (0.5 + 0.45 * Math.sin(time * 2.2));
                d.ring(p.x, y, p.z, r * 0.9, r, 48, soft, c);
                // «хвост» кольца — несколько бледных копий ниже по ходу движения
                for (int i = 1; i <= 4; i++) {
                    double yy = p.y + h * (0.5 + 0.45 * Math.sin(time * 2.2 - i * 0.12));
                    d.ring(p.x, yy, p.z, r * 0.9, r, 48, 0, Colors.mulAlpha(c, 0.35f - i * 0.07f));
                }
            }
        }
    }
}
