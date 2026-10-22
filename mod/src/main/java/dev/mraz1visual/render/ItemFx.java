package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.CustomSwords;
import dev.mraz1visual.module.FireHand;
import dev.mraz1visual.module.WeaponSkins;
import dev.mraz1visual.models.Mesh;
import dev.mraz1visual.models.ModelDraw;
import dev.mraz1visual.models.ModelEntry;
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
    /** Импортированный скин предмета, его состояние (натяжение лука и т. п.) и контекст отрисовки. */
    public static final RenderStateDataKey<ModelEntry> SKIN = RenderStateDataKey.create(() -> "mraz1visual:item_skin");
    public static final RenderStateDataKey<String> SKIN_STATE = RenderStateDataKey.create(() -> "mraz1visual:item_skin_state");
    public static final RenderStateDataKey<ItemDisplayContext> CTX = RenderStateDataKey.create(() -> "mraz1visual:item_ctx");

    /** Состояние предмета, чей слой сейчас отправляется (между submit предмета и слоя). */
    private static int currentFlags;
    private static Item currentItem;
    private static ModelEntry currentSkin;
    private static String currentState;
    private static ItemDisplayContext currentCtx;
    /** Поза скина применена к этому слою / скин этого предмета уже отправлен (у предмета бывает несколько слоёв). */
    private static boolean skinPose, skinDrawn;
    /** Предмет рисуется в превью/инвентаре (ортографическая камера) — ставит слой модели игрока. */
    public static boolean guiPass;

    private ItemFx() {}

    /** Из миксина ItemModelResolver.updateForTopItem: пометить предмет. */
    public static void tag(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, ItemOwner owner) {
        Minecraft mc = Minecraft.getInstance();
        int flags = 0;
        if (!stack.isEmpty() && mc.player != null && owner == mc.player) {
            flags |= MINE;
            if (stack.is(ItemTags.SWORDS)) flags |= SWORD | WEAPON;
            if (stack.is(ItemTags.AXES) || stack.is(net.minecraft.world.item.Items.TRIDENT) || stack.is(net.minecraft.world.item.Items.MACE)) flags |= WEAPON;
        }
        state.setData(FLAGS, flags == 0 ? null : flags);
        state.setData(ITEM, flags == 0 ? null : stack.getItem());
        ModelEntry skin = null;
        if ((flags & MINE) != 0 && WeaponSkins.active()) {
            skin = WeaponSkins.skinFor(stack);
            // иконки в хотбаре/инвентаре кэшируются по «личности» модели — без галочки не помечаем вовсе
            if (skin != null && ctx == ItemDisplayContext.GUI && !WeaponSkins.allowed(ctx, false)) skin = null;
        }
        state.setData(SKIN, skin);
        state.setData(SKIN_STATE, skin == null ? null : WeaponSkins.stateFor(stack));
        state.setData(CTX, skin == null ? null : ctx);
        if (skin != null) {
            // новый ключ кэша иконки: другой скин, состояние или поза — другая картинка
            state.appendModelIdentityElement(skin);
            state.appendModelIdentityElement(state.getData(SKIN_STATE));
            state.appendModelIdentityElement(skin.poseKey);
            if (skin.animatedTextures()) state.setAnimated();
        }
    }

    /** Из миксина ItemStackRenderState.submit: начало отправки предмета (до всех его слоёв). */
    public static void itemStart() {
        skinDrawn = false;
    }

    /** Из миксина ItemStackRenderState.submit: какой предмет сейчас отправляет слои. */
    public static void begin(ItemStackRenderState state) {
        Integer f = state.getData(FLAGS);
        currentFlags = f == null ? 0 : f;
        currentItem = state.getData(ITEM);
        currentSkin = state.getData(SKIN);
        currentState = state.getData(SKIN_STATE);
        currentCtx = state.getData(CTX);
        skinPose = false;
    }

    /** Предмет, который сейчас рисуется, — оружие (меч, топор, трезубец, булава). */
    public static boolean currentIsWeapon() {
        return (currentFlags & WEAPON) != 0;
    }

    public static void end() {
        currentFlags = 0;
        currentItem = null;
        currentSkin = null;
        skinPose = false;
    }

    /**
     * Из миксина вместо ванильной ItemTransform.apply: у скина своя поза (display его модели + ручная подстройка).
     * @return true — поза скина применена.
     */
    public static boolean applySkinTransform(boolean leftHand, PoseStack.Pose pose) {
        if (currentSkin == null || currentCtx == null || !WeaponSkins.allowed(currentCtx, guiPass)) return false;
        currentSkin.display[currentCtx.ordinal()].apply(leftHand, pose);
        skinPose = true;
        return true;
    }

    /** Скин вместо квадов или особой модели (щит, трезубец). @return true — ванильное не рисовать. */
    public static boolean submitSkin(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, int light, int overlay) {
        if (!skinPose) return false;
        if (!skinDrawn) {
            skinDrawn = true;
            Mesh m = currentSkin.mesh(currentState);
            WeaponSkins w = WeaponSkins.get();
            ModelDraw.submit(c, ps, m, null, -1, overlay, light, w != null && w.glow.get());
            if (ctx != ItemDisplayContext.GUI && ctx != ItemDisplayContext.GROUND && ctx != ItemDisplayContext.FIXED) {
                FireHand.submitAuraBounds(ps, c, ctx, m.bounds, guiPass);
            }
        }
        return true;
    }

    /**
     * Из миксина LayerRenderState.submit вместо отправки квадов. Поза — уже в пространстве модели предмета.
     * @return true — квады предмета заменены своим (ванильные не отправлять).
     */
    public static boolean submitLayer(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, List<BakedQuad> quads) {
        if ((currentFlags & MINE) == 0) return false;
        if (skinPose) return true;
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
