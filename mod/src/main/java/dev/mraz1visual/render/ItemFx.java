package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.CustomSwords;
import dev.mraz1visual.module.FireHand;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Эффекты прямо в пространстве модели предмета. Предмет помечается при сборке состояния
 * (предмет моего игрока? меч?), а при отправке квадов модели — уже после ванильной раскладки
 * (рука, взмах, покачивание камеры, 3-е лицо, иконка в GUI) — свой меч подставляется вместо квадов,
 * а аура рисуется по силуэту этих квадов. Поэтому всё стоит ровно там, где был бы ванильный предмет.
 */
public final class ItemFx {
    /** Флаги предмета: {@link #MINE} — предмет моего игрока, {@link #SWORD} — меч. */
    public static final RenderStateDataKey<Integer> FLAGS = RenderStateDataKey.create(() -> "mraz1visual:item_flags");
    public static final RenderStateDataKey<Item> ITEM = RenderStateDataKey.create(() -> "mraz1visual:item");
    public static final int MINE = 1, SWORD = 2, WEAPON = 4;

    /** Состояние предмета, чей слой сейчас отправляется (между submit предмета и слоя). */
    private static int currentFlags;
    private static Item currentItem;
    /** Предмет рисуется в превью/инвентаре (ортографическая камера) — ставит слой модели игрока. */
    public static boolean guiPass;

    private ItemFx() {}

    /** Из миксина ItemModelResolver.updateForTopItem: пометить предмет. */
    public static void tag(ItemStackRenderState state, ItemStack stack, ItemOwner owner) {
        Minecraft mc = Minecraft.getInstance();
        int flags = 0;
        if (!stack.isEmpty() && mc.player != null && owner == mc.player) {
            flags |= MINE;
            if (stack.is(ItemTags.SWORDS)) flags |= SWORD | WEAPON;
            if (stack.is(ItemTags.AXES) || stack.is(net.minecraft.world.item.Items.TRIDENT) || stack.is(net.minecraft.world.item.Items.MACE)) flags |= WEAPON;
        }
        state.setData(FLAGS, flags == 0 ? null : flags);
        state.setData(ITEM, flags == 0 ? null : stack.getItem());
    }

    /** Из миксина ItemStackRenderState.submit: какой предмет сейчас отправляет слои. */
    public static void begin(ItemStackRenderState state) {
        Integer f = state.getData(FLAGS);
        currentFlags = f == null ? 0 : f;
        currentItem = state.getData(ITEM);
    }

    /** Предмет, который сейчас рисуется, — оружие (меч, топор, трезубец, булава). */
    public static boolean currentIsWeapon() {
        return (currentFlags & WEAPON) != 0;
    }

    public static void end() {
        currentFlags = 0;
        currentItem = null;
    }

    /**
     * Из миксина LayerRenderState.submit вместо отправки квадов. Поза — уже в пространстве модели предмета.
     * @return true — квады предмета заменены своим (ванильные не отправлять).
     */
    public static boolean submitLayer(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, List<BakedQuad> quads) {
        if ((currentFlags & MINE) == 0) return false;
        boolean replaced = (currentFlags & SWORD) != 0 && CustomSwords.submitModel(ps, c, ctx, quads, currentItem, guiPass);
        if (ctx != ItemDisplayContext.GUI && ctx != ItemDisplayContext.GROUND && ctx != ItemDisplayContext.FIXED) {
            FireHand.submitAura(ps, c, ctx, quads, replaced, guiPass);
        }
        return replaced;
    }

    // ── силуэт модели предмета ───────────────────────────────

    /** Границы модели предмета в её пространстве: minX, minY, minZ, maxX, maxY, maxZ. */
    public static boolean bounds(List<BakedQuad> quads, float[] out) {
        if (quads == null || quads.isEmpty()) return false;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (BakedQuad q : quads) {
            for (int i = 0; i < 4; i++) {
                var p = q.position(i);
                x0 = Math.min(x0, p.x()); y0 = Math.min(y0, p.y()); z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x()); y1 = Math.max(y1, p.y()); z1 = Math.max(z1, p.z());
            }
        }
        out[0] = x0; out[1] = y0; out[2] = z0; out[3] = x1; out[4] = y1; out[5] = z1;
        return true;
    }
}
