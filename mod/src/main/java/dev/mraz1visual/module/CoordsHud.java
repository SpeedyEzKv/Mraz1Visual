package dev.mraz1visual.module;

import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Координаты, направление взгляда и биом. Строки пересобираются только при смене блока / стороны света. */
public final class CoordsHud extends TextHud {
    final BoolSetting direction = add(new BoolSetting("direction", "Направление", true));
    final BoolSetting biome = add(new BoolSetting("biome", "Биом", true));
    final BoolSetting nether = add(new BoolSetting("nether", "Координаты Незера / Верхнего мира", false));

    private int lx = Integer.MIN_VALUE, ly, lz;
    private Direction lastDir;
    private String coords = "", dir = "", biomeName = "", other = "";

    public CoordsHud() {
        super("hud_coords", "Coordinates", "Координаты, направление и биом.", 0.0, 0.22);
    }

    @Override
    protected void update(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        labels[0] = "XYZ";
        if (StreamerMode.hidesCoords()) {
            values[0] = "скрыто";
            lines = 1;
            return;
        }
        int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
        if (x != lx || y != ly || z != lz) {
            lx = x; ly = y; lz = z;
            coords = x + "  " + y + "  " + z;
            if (biome.get()) biomeName = biomeName(mc, p.blockPosition());
            other = otherDimension(mc, x, z);
        }
        Direction d = p.getDirection();
        if (d != lastDir) {
            lastDir = d;
            dir = dirName(d);
        }
        int n = 0;
        values[n++] = coords;
        if (nether.get() && !other.isEmpty()) {
            labels[n] = mc.level.dimension() == Level.NETHER ? "Мир" : "Незер";
            values[n++] = other;
        }
        if (direction.get()) {
            labels[n] = "Взгляд";
            values[n++] = dir;
        }
        if (biome.get()) {
            labels[n] = "Биом";
            values[n++] = biomeName;
        }
        lines = n;
    }

    private static String otherDimension(Minecraft mc, int x, int z) {
        var dim = mc.level.dimension();
        if (dim == Level.NETHER) return x * 8 + "  " + z * 8;
        if (dim == Level.OVERWORLD) return Math.floorDiv(x, 8) + "  " + Math.floorDiv(z, 8);
        return "";
    }

    private static String biomeName(Minecraft mc, BlockPos pos) {
        try {
            String id = mc.level.getBiome(pos).getRegisteredName();
            int c = id.indexOf(':');
            String key = "biome." + (c < 0 ? "minecraft." + id : id.substring(0, c) + "." + id.substring(c + 1));
            return Component.translatable(key).getString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String dirName(Direction d) {
        return switch (d) {
            case NORTH -> "Север (−Z)";
            case SOUTH -> "Юг (+Z)";
            case WEST -> "Запад (−X)";
            case EAST -> "Восток (+X)";
            default -> d.getName();
        };
    }
}
