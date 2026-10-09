package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.ColorSetting;
import dev.umbra.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Траектория твоего броска: жемчуг, снежок, яйцо, зелья, стрела, трезубец, арбалет.
 * Считается раз в тик по ванильной физике, только до первого блока. Только твой снаряд.
 */
public final class Trajectories extends Module implements WorldLayer {
    private static final int MAX = 160;

    final ColorSetting color = add(new ColorSetting("color", "Линия", 0xC0E6E4FF));
    final ColorSetting landColor = add(new ColorSetting("land", "Точка падения", 0xE0B8B4F2));

    private final double[] xs = new double[MAX], ys = new double[MAX], zs = new double[MAX];
    private int count;
    private boolean landed;

    public Trajectories() {
        super("trajectories", "Trajectories", "Линия полёта твоего жемчуга, стрелы и зелий.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        count = 0;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ItemStack stack = p.getMainHandItem();
        double speed, gravity, drag = 0.99, pitchOffset = 0;
        Item it = stack.getItem();
        if (it == Items.ENDER_PEARL || it == Items.SNOWBALL || it == Items.EGG) {
            speed = 1.5; gravity = 0.03;
        } else if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) {
            speed = 0.5; gravity = 0.05; pitchOffset = -20;
        } else if (it == Items.EXPERIENCE_BOTTLE) {
            speed = 0.7; gravity = 0.07; pitchOffset = -20;
        } else if (it == Items.BOW) {
            if (!p.isUsingItem() || p.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
            float f = p.getTicksUsingItem() / 20f;
            f = (f * f + f * 2) / 3;
            if (f < 0.1f) return;
            speed = Math.min(1f, f) * 3.0; gravity = 0.05;
        } else if (it == Items.TRIDENT) {
            if (!p.isUsingItem()) return;
            speed = 2.5; gravity = 0.05;
        } else if (it == Items.CROSSBOW && CrossbowItem.isCharged(stack)) {
            speed = 3.15; gravity = 0.05;
        } else {
            return;
        }

        float yaw = (float) Math.toRadians(p.getYRot()), pitch = (float) Math.toRadians(p.getXRot() + pitchOffset);
        double vx = -Math.sin(yaw) * Math.cos(pitch) * speed;
        double vy = -Math.sin(pitch) * speed;
        double vz = Math.cos(yaw) * Math.cos(pitch) * speed;
        Vec3 m = p.getDeltaMovement();
        vx += m.x;
        vz += m.z;
        if (!p.onGround()) vy += m.y;

        double x = p.getX(), y = p.getEyeY() - 0.1, z = p.getZ();
        landed = false;
        for (int i = 0; i < MAX; i++) {
            xs[i] = x; ys[i] = y; zs[i] = z;
            count = i + 1;
            double nx = x + vx, ny = y + vy, nz = z + vz;
            BlockHitResult hit = mc.level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(nx, ny, nz),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 l = hit.getLocation();
                if (count < MAX) {
                    xs[count] = l.x; ys[count] = l.y; zs[count] = l.z;
                    count++;
                }
                landed = true;
                break;
            }
            x = nx; y = ny; z = nz;
            vx *= drag; vy *= drag; vz *= drag;
            vy -= gravity;
            if (y < mc.level.getMinY() - 16) break;
        }
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        if (count < 2) return;
        int c = color.get();
        double w = 0.03;
        for (int i = 1; i < count; i++) {
            float a = Math.min(1f, i / 4f); // у глаз линия тает, чтобы не мешать
            int col = Colors.mulAlpha(c, a);
            // две перекрещенные ленты — линия видна с любого угла
            d.quad(xs[i - 1] - w, ys[i - 1], zs[i - 1], xs[i] - w, ys[i], zs[i], xs[i] + w, ys[i], zs[i], xs[i - 1] + w, ys[i - 1], zs[i - 1], col, col, col, col);
            d.quad(xs[i - 1], ys[i - 1] - w, zs[i - 1], xs[i], ys[i] - w, zs[i], xs[i], ys[i] + w, zs[i], xs[i - 1], ys[i - 1] + w, zs[i - 1], col, col, col, col);
        }
        if (landed) {
            int l = landColor.get();
            d.ring(xs[count - 1], ys[count - 1] + 0.02, zs[count - 1], 0.25, 0.4, 32, Colors.mulAlpha(l, 0.2f), l);
        }
    }
}
