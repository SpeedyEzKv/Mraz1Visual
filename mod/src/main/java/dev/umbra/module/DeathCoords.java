package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Координаты смерти: в чат (только тебе), в буфер обмена и луч-маяк на месте смерти. */
public final class DeathCoords extends Module implements WorldLayer {
    final BoolSetting chat = add(new BoolSetting("chat", "Координаты в чат (только тебе)", true));
    final BoolSetting copy = add(new BoolSetting("copy", "Копировать в буфер обмена", false));
    final BoolSetting beam = add(new BoolSetting("beam", "Луч на месте смерти", true));
    final ColorSetting color = add(new ColorSetting("color", "Цвет луча", 0xC0E05A5A));

    private boolean wasDead;
    private String dim;
    private double x, y, z;
    private boolean has;

    public DeathCoords() {
        super("death_coords", "Death Coords", "Где ты умер: в чат и лучом в мире.", Category.MISC);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead) {
            int bx = p.getBlockX(), by = p.getBlockY(), bz = p.getBlockZ();
            String text = bx + " " + by + " " + bz;
            if (chat.get()) p.displayClientMessage(Component.literal("§7Umbra: ты умер на §f" + text), false);
            if (copy.get()) mc.keyboardHandler.setClipboard(text);
            dim = mc.level.dimension().toString();
            x = bx + 0.5;
            y = by;
            z = bz + 0.5;
            has = true;
        }
        wasDead = dead;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!has || !beam.get() || mc.level == null || mc.player == null || !mc.level.dimension().toString().equals(dim)) return;
        double dist = Math.sqrt(mc.player.distanceToSqr(x, y, z));
        if (dist < 2) return;
        float fade = (float) Math.min(1, Math.max(0.25, dist / 24));
        d.beam(x, y, z, 96, 0.22, Colors.mulAlpha(color.get(), fade));
    }
}
