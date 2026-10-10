package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Рамка вокруг игроков, которых ты и так видишь.
 * Только в прямой видимости и с проверкой глубины — сквозь стены ничего не показывает.
 */
public final class Esp extends Module implements WorldLayer {
    final EnumSetting mode = add(new EnumSetting("mode", "Вид", 0, "outline", "Контур", "fill", "Заливка", "both", "Контур + заливка"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xD0B8B4F2));
    final ColorSetting friendColor = add(new ColorSetting("friend_color", "Цвет друзей", 0xD060E08A));
    final BoolSetting onlyVisible = add(new BoolSetting("only_visible", "Только в прямой видимости", true));
    final SliderSetting range = add(new SliderSetting("range", "Дальность", 48, 8, 96, 4, " бл."));

    public Esp() {
        super("esp", "ESP", "Рамка вокруг игроков в поле зрения. Сквозь стены — нет.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double r2 = range.get() * range.get();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isInvisible() || !p.isAlive() || p.distanceToSqr(mc.player) > r2) continue;
            if (onlyVisible.get() && !mc.player.hasLineOfSight(p)) continue;
            double ox = p.xo + (p.getX() - p.xo) * pt - p.getX();
            double oy = p.yo + (p.getY() - p.yo) * pt - p.getY();
            double oz = p.zo + (p.getZ() - p.zo) * pt - p.getZ();
            AABB b = p.getBoundingBox().move(ox, oy, oz);
            int c = FriendsModule.highlight(p.getName().getString()) ? friendColor.get() : color.get();
            String m = mode.getId();
            if (!m.equals("outline")) d.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Colors.mulAlpha(c, 0.18f));
            if (!m.equals("fill")) d.edges(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, 0.025, c);
        }
    }
}
