package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Хитбоксы как F3+B, но со своими цветами. С проверкой глубины — сквозь стены не видно. */
public final class Hitboxes extends Module implements WorldLayer {
    final BoolSetting players = add(new BoolSetting("players", "Игроки", true).group("Кому"));
    final BoolSetting mobs = add(new BoolSetting("mobs", "Мобы", true).group("Кому"));
    final BoolSetting items = add(new BoolSetting("items", "Предметы", false).group("Кому"));
    final SliderSetting range = add(new SliderSetting("range", "Дальность", 24, 4, 64, 1, " бл.").group("Кому"));
    final ColorSetting playerColor = add(new ColorSetting("player_color", "Игроки", 0xC0E6E4FF).group("Цвета"));
    final ColorSetting mobColor = add(new ColorSetting("mob_color", "Мобы", 0x90B8B4F2).group("Цвета"));
    final ColorSetting itemColor = add(new ColorSetting("item_color", "Предметы", 0x80F2E7C4).group("Цвета"));

    public Hitboxes() {
        super("hitboxes", "Hitboxes", "Контуры хитбоксов, как F3+B, со своими цветами.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (e.isInvisible() || e.distanceToSqr(mc.player) > r2) continue;
            int c;
            if (e instanceof Player) {
                if (!players.get()) continue;
                c = playerColor.get();
            } else if (e instanceof LivingEntity) {
                if (!mobs.get()) continue;
                c = mobColor.get();
            } else if (e instanceof ItemEntity) {
                if (!items.get()) continue;
                c = itemColor.get();
            } else continue;
            Vec3 lerp = e.getPosition(pt);
            AABB b = e.getBoundingBox();
            double ox = lerp.x - e.getX(), oy = lerp.y - e.getY(), oz = lerp.z - e.getZ();
            d.edges(b.minX + ox, b.minY + oy, b.minZ + oz, b.maxX + ox, b.maxY + oy, b.maxZ + oz, 0.012, c);
        }
    }
}
