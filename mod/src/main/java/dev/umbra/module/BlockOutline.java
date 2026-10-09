package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.WorldLayer;
import dev.umbra.render.WorldDraw;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Своя подсветка блока под прицелом: заливка + рёбра, плавно переезжает между блоками. */
public final class BlockOutline extends Module implements WorldLayer {
    final ColorSetting fill = add(new ColorSetting("fill", "Заливка", 0x22B8B4F2));
    final ColorSetting edge = add(new ColorSetting("edge", "Контур", 0xC0E6E4FF));
    final SliderSetting width = add(new SliderSetting("width", "Толщина", 2, 1, 6, 1, " px"));
    final BoolSetting smooth = add(new BoolSetting("smooth", "Плавное движение", true));

    private double ax1, ay1, az1, ax2, ay2, az2;
    private boolean has;
    private long last;

    public BlockOutline() {
        super("block_outline", "Block Outline", "Заливка и контур блока под прицелом.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            has = false;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            has = false;
            return;
        }
        VoxelShape shape = state.getShape(mc.level, pos);
        if (shape.isEmpty()) {
            has = false;
            return;
        }
        AABB b = shape.bounds().move(pos).inflate(0.002);

        long now = System.nanoTime();
        float dt = last == 0 ? 1f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (!has || !smooth.get()) {
            ax1 = b.minX; ay1 = b.minY; az1 = b.minZ; ax2 = b.maxX; ay2 = b.maxY; az2 = b.maxZ;
            has = true;
        } else {
            float k = Anim.to(0f, 1f, dt * 1.4f);
            ax1 += (b.minX - ax1) * k; ay1 += (b.minY - ay1) * k; az1 += (b.minZ - az1) * k;
            ax2 += (b.maxX - ax2) * k; ay2 += (b.maxY - ay2) * k; az2 += (b.maxZ - az2) * k;
        }
        if ((fill.get() >>> 24) != 0) d.box(ax1, ay1, az1, ax2, ay2, az2, fill.get());
        if ((edge.get() >>> 24) != 0) d.edges(ax1, ay1, az1, ax2, ay2, az2, width.get() * 0.004, edge.get());
    }
}
