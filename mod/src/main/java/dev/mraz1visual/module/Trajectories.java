package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Линия полёта того, что в руке: жемчуг, снежки, яйца, зелья, пузырьки опыта, лук (по натяжению), арбалет, трезубец.
 * Считается только у себя по ванильной физике (скорость, гравитация, сопротивление воздуха), точка падения — кольцо.
 * Без аллокаций: путь копится в массивах, столкновение — по форме блока в точке.
 */
public final class Trajectories extends Module implements WorldLayer {
    private static final int MAX = 400, SUB = 4;

    final ColorSetting color = add(new ColorSetting("color", "Цвет линии", 0xFF8FD6FF));
    final ColorSetting landColor = add(new ColorSetting("land_color", "Цвет точки падения", 0xFFFFD27A));
    final SliderSetting width = add(new SliderSetting("width", "Толщина", 0.04, 0.01, 0.12, 0.01, " бл."));
    final SliderSetting ticks = add(new SliderSetting("ticks", "Длина расчёта", 100, 20, 200, 10, " тиков"));
    final BoolSetting offhand = add(new BoolSetting("offhand", "Учитывать вторую руку", true));

    private final double[] px = new double[MAX], py = new double[MAX], pz = new double[MAX];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public Trajectories() {
        super("trajectories", "Trajectories", "Линия полёта жемчуга, стрел, зелий и трезубца с точкой падения.", Category.PVP);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || !mc.options.getCameraType().isFirstPerson()) return;

        ItemStack stack = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (kind(stack, p) == null && offhand.get()) stack = p.getItemInHand(InteractionHand.OFF_HAND);
        double[] k = kind(stack, p);
        if (k == null) return;
        double speed = k[0], gravity = k[1], drag = k[2], pitchOffset = k[3];

        float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch), rp2 = Math.toRadians(pitch + pitchOffset);
        double vx = -Math.sin(ry) * Math.cos(rp) * speed;
        double vy = -Math.sin(rp2) * speed;
        double vz = Math.cos(ry) * Math.cos(rp) * speed;
        // как в ванили: к броску прибавляется скорость самого игрока
        var mv = p.getDeltaMovement();
        vx += mv.x;
        vz += mv.z;
        if (!p.onGround()) vy += mv.y;

        double x = net.minecraft.util.Mth.lerp(pt, p.xo, p.getX());
        double y = net.minecraft.util.Mth.lerp(pt, p.yo, p.getY()) + p.getEyeHeight() - 0.1;
        double z = net.minecraft.util.Mth.lerp(pt, p.zo, p.getZ());
        // линия начинается чуть правее и ниже глаза, иначе её не видно
        double sx = x - Math.cos(ry) * 0.16, sy = y - 0.1, sz = z - Math.sin(ry) * 0.16;

        int n = 0, steps = ticks.getI();
        px[n] = sx; py[n] = sy; pz[n] = sz; n++;
        boolean hit = false;
        for (int t = 0; t < steps && n < MAX && !hit; t++) {
            for (int s = 0; s < SUB; s++) {
                x += vx / SUB;
                y += vy / SUB;
                z += vz / SUB;
                if (solid(level, x, y, z)) { hit = true; break; }
            }
            vx *= drag;
            vy = vy * drag - gravity;
            vz *= drag;
            if (y < level.getMinY() - 16) break;
            px[n] = x; py[n] = y; pz[n] = z; n++;
        }
        if (hit && n < MAX) { px[n] = x; py[n] = y; pz[n] = z; n++; }

        int c = color.get();
        double w = width.get();
        for (int i = 1; i < n; i++) {
            // линия тает у игрока, чтобы не перекрывать прицел
            float a = Math.min(1f, i / 4f);
            d.line(px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i], w, Colors.mulAlpha(c, a));
        }
        if (hit) {
            int lc = landColor.get();
            d.ring(x, y + 0.02, z, 0.22, 0.32, 24, Colors.mulAlpha(lc, 0.9f), Colors.mulAlpha(lc, 0.3f));
            d.billboardDot(x, y + 0.05, z, 0.12, lc);
        }
    }

    private boolean solid(ClientLevel level, double x, double y, double z) {
        pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return false;
        var shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double lx = x - pos.getX(), ly = y - pos.getY(), lz = z - pos.getZ();
        for (var box : shape.toAabbs()) {
            if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) return true;
        }
        return false;
    }

    // скорость, гравитация, сопротивление, сдвиг угла броска — по ванильным снарядам
    private static final double[] PEARL = {1.5, 0.03, 0.99, 0}, POTION = {0.5, 0.05, 0.99, -20}, XP = {0.7, 0.07, 0.99, -20},
            TRIDENT = {2.5, 0.05, 0.99, 0}, CROSSBOW = {3.15, 0.05, 0.99, 0};
    private final double[] bow = {0, 0.05, 0.99, 0};

    private double[] kind(ItemStack s, LocalPlayer p) {
        if (s.isEmpty()) return null;
        if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)) return PEARL;
        if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return POTION;
        if (s.is(Items.EXPERIENCE_BOTTLE)) return XP;
        if (s.is(Items.TRIDENT)) return p.isUsingItem() ? TRIDENT : null;
        if (s.is(Items.CROSSBOW)) return CROSSBOW;
        if (s.is(Items.BOW)) {
            if (!p.isUsingItem()) return null;
            float f = p.getTicksUsingItem() / 20f;
            f = Math.min(1f, (f * f + f * 2f) / 3f);
            if (f < 0.1f) return null;
            bow[0] = f * 3.0;
            return bow;
        }
        return null;
    }
}
