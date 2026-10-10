package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Свои таблички с ником, здоровьем и расстоянием вместо ванильных.
 * Рисуются с проверкой глубины: сквозь стены не видно — как обычные ники у присевших.
 */
public final class NameTags extends Module implements WorldLayer {
    private static NameTags instance;
    final BoolSetting health = add(new BoolSetting("health", "Здоровье", true));
    final BoolSetting distance = add(new BoolSetting("distance", "Расстояние", false));
    final BoolSetting background = add(new BoolSetting("background", "Подложка", true));
    final SliderSetting scale = add(new SliderSetting("scale", "Размер", 1.0, 0.5, 2.0, 0.05, "×"));
    final SliderSetting range = add(new SliderSetting("range", "Дальность", 48, 8, 96, 4, " бл."));

    public NameTags() {
        super("name_tags", "Name Tags", "Ник, здоровье и расстояние над игроками — аккуратнее ванильных.", Category.VISUALS);
        instance = this;
    }

    /** Из миксина EntityRenderer.shouldShowName: прячем ванильный ник у игроков. */
    public static boolean hidesVanilla(Entity e) {
        return instance != null && instance.isEnabled() && e instanceof Player && e != Minecraft.getInstance().player;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double r2 = range.get() * range.get();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isInvisible() || !p.isAlive()) continue;
            if (p.distanceToSqr(mc.player) > r2) continue;
            double x = p.xo + (p.getX() - p.xo) * pt;
            double y = p.yo + (p.getY() - p.yo) * pt + p.getBbHeight() + 0.45;
            double z = p.zo + (p.getZ() - p.zo) * pt;
            String name = p.getName().getString();
            MutableComponent text = Component.literal(name).withStyle(FriendsModule.highlight(name) ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            if (health.get()) {
                float hp = p.getHealth() + p.getAbsorptionAmount();
                ChatFormatting c = hp > 14 ? ChatFormatting.GREEN : hp > 7 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                text.append(Component.literal("  " + Math.round(hp) + "♥").withStyle(c));
            }
            if (distance.get()) {
                text.append(Component.literal("  " + Math.round(Math.sqrt(p.distanceToSqr(mc.player))) + "м").withStyle(ChatFormatting.GRAY));
            }
            double dist = Math.sqrt(Math.pow(x - d.camX(), 2) + Math.pow(y - d.camY(), 2) + Math.pow(z - d.camZ(), 2));
            float s = scale.getF() * (float) Math.max(1.0, dist / 10.0);
            d.label(text, x, y, z, s, 0xFFFFFFFF, background.get() ? 0x70000000 : 0);
        }
    }
}
