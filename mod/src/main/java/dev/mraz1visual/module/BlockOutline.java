package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Выделение блока под прицелом: тонкие рёбра с градиентом (верх ярче), едва заметная заливка, мягкий ореол;
 * при смене блока рамка плавно «перетекает» с лёгким пульсом, прогресс ломания заполняет блок изнутри.
 */
public final class BlockOutline extends Module implements WorldLayer {
    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0, "glass", "Стекло", "edges", "Линия", "fill", "Заливка", "glow", "Шейдер (свечение)", "gradient", "Градиент"));
    final SliderSetting gradientSpeed = add(new SliderSetting("gradient_speed", "Скорость градиента", 1, 0.2, 4, 0.1, "×"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 6, Palette.OPTIONS));
    final ColorSetting fill = add(new ColorSetting("fill", "Свой: заливка", 0x22B8B4F2));
    final ColorSetting edge = add(new ColorSetting("edge", "Свой: контур", 0xC0E6E4FF));
    final SliderSetting width = add(new SliderSetting("width", "Толщина", 2, 1, 6, 1, " px"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 85, 20, 100, 5, " %"));
    final BoolSetting smooth = add(new BoolSetting("smooth", "Плавное движение", true));
    final BoolSetting progress = add(new BoolSetting("progress", "Прогресс ломания", true));

    private double ax1, ay1, az1, ax2, ay2, az2;
    private boolean has;
    private long last;
    private BlockPos lastPos;
    private float pulse, shownProgress, alpha;

    public BlockOutline() {
        super("block_outline", "Block Outline", "Рамка блока: линия, свечение, заливка или переливающийся градиент.", Category.VISUALS);
        markNew();
    }

    /** Цвет ребра k для градиентного стиля. */
    private int gc(double phase, int k, float a) {
        float u = (float) (0.5 + 0.5 * Math.sin(phase + k * 0.52));
        return Colors.mulAlpha(dev.mraz1visual.render.HudStyle.grad(u), a * opacity.getF() / 100f);
    }

    private int col(float t, float a) {
        if (preset.getId().equals("custom")) return Colors.mulAlpha(t < 0.5f ? edge.get() : fill.get(), a * opacity.getF() / 100f);
        return Fx.color(preset.getId(), t, Fx.time() * 0.2f, edge.get(), fill.get(), a * opacity.getF() / 100f);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        BlockPos pos = null;
        AABB b = null;
        if (mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            VoxelShape shape = state.isAir() ? null : state.getShape(mc.level, pos);
            if (shape != null && !shape.isEmpty()) b = shape.bounds().move(pos).inflate(0.003);
        }
        alpha = Ease.approach(alpha, b != null ? 1 : 0, b != null ? 30 : 14, dt);
        if (b == null) {
            if (alpha < 0.02f) has = false;
            if (!has) return;
        } else if (!has || !smooth.get()) {
            ax1 = b.minX; ay1 = b.minY; az1 = b.minZ; ax2 = b.maxX; ay2 = b.maxY; az2 = b.maxZ;
            has = true;
        } else {
            ax1 = Ease.approach(ax1, b.minX, 26, dt); ay1 = Ease.approach(ay1, b.minY, 26, dt); az1 = Ease.approach(az1, b.minZ, 26, dt);
            ax2 = Ease.approach(ax2, b.maxX, 26, dt); ay2 = Ease.approach(ay2, b.maxY, 26, dt); az2 = Ease.approach(az2, b.maxZ, 26, dt);
        }
        if (pos != null && !pos.equals(lastPos)) {
            lastPos = pos.immutable();
            pulse = 1f;
        }
        pulse = Ease.approach(pulse, 0, 7, dt);
        float A = alpha;
        // лёгкий «вдох» рамки при смене блока
        double gx = (ax2 - ax1) * 0.03 * Ease.smooth(pulse), gy = (ay2 - ay1) * 0.03 * Ease.smooth(pulse), gz = (az2 - az1) * 0.03 * Ease.smooth(pulse);
        double x1 = ax1 - gx, y1 = ay1 - gy, z1 = az1 - gz, x2 = ax2 + gx, y2 = ay2 + gy, z2 = az2 + gz;
        String st = style.getId();
        if (!st.equals("edges")) d.box(x1, y1, z1, x2, y2, z2, col(0.6f, A * (st.equals("fill") ? 0.28f : 0.1f)));
        if (!st.equals("fill")) {
            double t = width.get() * 0.0035;
            int top = col(0.05f, A), mid = col(0.3f, A * 0.75f), bottom = col(0.55f, A * 0.45f);
            // верх ярче, низ спокойнее — объём без «жёсткой» рамки
            d.line(x1, y2, z1, x2, y2, z1, t, top); d.line(x2, y2, z1, x2, y2, z2, t, top);
            d.line(x2, y2, z2, x1, y2, z2, t, top); d.line(x1, y2, z2, x1, y2, z1, t, top);
            d.line(x1, y1, z1, x1, y2, z1, t, mid); d.line(x2, y1, z1, x2, y2, z1, t, mid);
            d.line(x2, y1, z2, x2, y2, z2, t, mid); d.line(x1, y1, z2, x1, y2, z2, t, mid);
            d.line(x1, y1, z1, x2, y1, z1, t, bottom); d.line(x2, y1, z1, x2, y1, z2, t, bottom);
            d.line(x2, y1, z2, x1, y1, z2, t, bottom); d.line(x1, y1, z2, x1, y1, z1, t, bottom);
            if (st.equals("gradient")) {
                // переливающийся контур: каждое ребро — свой участок градиента, волна бежит по кругу
                double ph = Fx.time() * gradientSpeed.get() * 2;
                d.line(x1, y2, z1, x2, y2, z1, t * 1.4, gc(ph, 0, A)); d.line(x2, y2, z1, x2, y2, z2, t * 1.4, gc(ph, 1, A));
                d.line(x2, y2, z2, x1, y2, z2, t * 1.4, gc(ph, 2, A)); d.line(x1, y2, z2, x1, y2, z1, t * 1.4, gc(ph, 3, A));
                d.line(x1, y1, z1, x1, y2, z1, t * 1.4, gc(ph, 4, A)); d.line(x2, y1, z1, x2, y2, z1, t * 1.4, gc(ph, 5, A));
                d.line(x2, y1, z2, x2, y2, z2, t * 1.4, gc(ph, 6, A)); d.line(x1, y1, z2, x1, y2, z2, t * 1.4, gc(ph, 7, A));
                d.line(x1, y1, z1, x2, y1, z1, t * 1.4, gc(ph, 8, A)); d.line(x2, y1, z1, x2, y1, z2, t * 1.4, gc(ph, 9, A));
                d.line(x2, y1, z2, x1, y1, z2, t * 1.4, gc(ph, 10, A)); d.line(x1, y1, z2, x1, y1, z1, t * 1.4, gc(ph, 11, A));
            }
            if (st.equals("glow")) {
                // «шейдер»: мягкое свечение рёбер в проходе свечения
                d.glowBegin();
                d.edges(x1, y1, z1, x2, y2, z2, t * 7, col(0.3f, A * 0.45f));
                d.edges(x1, y1, z1, x2, y2, z2, t * 3, col(0.2f, A * 0.6f));
                d.glowEnd();
            }
            if (st.equals("glass")) {
                d.glowBegin();
                d.edges(x1, y1, z1, x2, y2, z2, t * 5, col(0.4f, A * (0.12f + 0.2f * pulse)));
                d.glowEnd();
            }
        }
        // прогресс ломания: блок заполняется изнутри от центра
        int stage = progress.get() && mc.gameMode != null ? mc.gameMode.getDestroyStage() : -1;
        shownProgress = Ease.approach(shownProgress, stage < 0 ? 0 : (stage + 1) / 10f, 16, dt);
        if (shownProgress > 0.01f) {
            double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
            double k = shownProgress / 2;
            d.box(cx - (x2 - x1) * k, cy - (y2 - y1) * k, cz - (z2 - z1) * k, cx + (x2 - x1) * k, cy + (y2 - y1) * k, cz + (z2 - z1) * k,
                    col(0.35f, A * 0.3f));
        }
    }
}
