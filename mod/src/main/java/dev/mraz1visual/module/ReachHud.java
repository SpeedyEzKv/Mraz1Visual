package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Дистанция последнего удара: от глаз до ближайшей точки хитбокса цели. Гаснет через 3 секунды. */
public final class ReachHud extends TextHud implements AttackListener {
    private long at;
    private boolean shown;

    public ReachHud() {
        super("hud_reach", "Reach Display", "Дистанция последнего удара.", 0.0, 0.58);
        labels[0] = "Дистанция";
        values[0] = "—";
    }

    @Override
    public void onAttack(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        AABB b = e.getBoundingBox();
        double dx = Math.max(b.minX - eye.x, Math.max(0, eye.x - b.maxX));
        double dy = Math.max(b.minY - eye.y, Math.max(0, eye.y - b.maxY));
        double dz = Math.max(b.minZ - eye.z, Math.max(0, eye.z - b.maxZ));
        int c = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 100);
        values[0] = c / 100 + "." + (c % 100 < 10 ? "0" : "") + c % 100 + " бл.";
        at = System.currentTimeMillis();
        shown = true;
    }

    @Override
    protected void update(Minecraft mc) {
        if (shown && System.currentTimeMillis() - at > 3000) {
            shown = false;
            values[0] = "—";
        }
    }
}
