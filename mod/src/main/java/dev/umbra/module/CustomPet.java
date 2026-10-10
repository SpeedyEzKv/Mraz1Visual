package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Питомец летает рядом с тобой. Существует только у тебя в клиенте —
 * сервер и другие игроки его не видят.
 */
public final class CustomPet extends Module {
    private static final int ID = -0x0FE7;
    final EnumSetting kind = add(new EnumSetting("kind", "Питомец", 0,
            "allay", "Тихоня", "bee", "Пчела", "parrot", "Попугай", "fox", "Лиса", "cat", "Кошка", "axolotl", "Аксолотль", "frog", "Лягушка", "chicken", "Цыплёнок"));
    final BoolSetting baby = add(new BoolSetting("baby", "Детёныш", true));
    final EnumSetting side = add(new EnumSetting("side", "Где", 0, "right", "Справа", "left", "Слева", "back", "Сзади"));

    private Entity pet;
    private String built = "";
    private double px, py, pz;

    public CustomPet() {
        super("custom_pet", "Custom Pet", "Питомец рядом с тобой. Виден только тебе.", Category.VISUALS);
    }

    @Override
    protected void onDisable() {
        remove();
    }

    private EntityType<?> type() {
        return switch (kind.getId()) {
            case "bee" -> EntityType.BEE;
            case "parrot" -> EntityType.PARROT;
            case "fox" -> EntityType.FOX;
            case "cat" -> EntityType.CAT;
            case "axolotl" -> EntityType.AXOLOTL;
            case "frog" -> EntityType.FROG;
            case "chicken" -> EntityType.CHICKEN;
            default -> EntityType.ALLAY;
        };
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            pet = null;
            return;
        }
        String key = kind.getId() + baby.get();
        if (pet == null || pet.level() != mc.level || !key.equals(built)) {
            remove();
            Entity e = type().create(mc.level, EntitySpawnReason.LOAD);
            if (e == null) return;
            e.setId(ID);
            if (e instanceof Mob m) m.setNoAi(true);
            if (e instanceof AgeableMob a && baby.get()) a.setBaby(true);
            e.setNoGravity(true);
            e.setSilent(true);
            px = p.getX();
            py = p.getY() + 1;
            pz = p.getZ();
            e.setPos(px, py, pz);
            e.setYRot(p.getYRot());
            mc.level.addEntity(e);
            pet = e;
            built = key;
        }
        // точка рядом с плечом, питомец плавно догоняет её
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        double off = switch (side.getId()) { case "left" -> -1; case "back" -> 0; default -> 1; };
        double back = side.getId().equals("back") ? 1.2 : 0.4;
        double tx = p.getX() + rx * off * 0.9 - fx * back;
        double tz = p.getZ() + rz * off * 0.9 - fz * back;
        double ty = p.getY() + 1.3 + Math.sin(p.tickCount * 0.12) * 0.12;
        px += (tx - px) * 0.25;
        py += (ty - py) * 0.25;
        pz += (tz - pz) * 0.25;
        pet.setPos(px, py, pz);
        pet.setYRot(p.getYRot());
        pet.setYHeadRot(p.getYHeadRot());
        pet.setYBodyRot(p.getYRot());
        pet.setXRot(0);
    }

    private void remove() {
        Minecraft mc = Minecraft.getInstance();
        if (pet != null && mc.level != null && pet.level() == mc.level) mc.level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        pet = null;
        built = "";
    }
}
