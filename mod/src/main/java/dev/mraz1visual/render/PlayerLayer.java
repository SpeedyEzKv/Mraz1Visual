package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.Pets;
import dev.mraz1visual.module.Wings3D;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Слой модели игрока: косметика рисуется у самой модели, а не в мире отдельно. Поэтому она видна
 * от 3-го лица, в инвентаре и в превью меню, а поза тела (приседание, элитры, плавание) учитывается сама.
 * Только для своего игрока: чужие модели не трогаем.
 */
public final class PlayerLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Метка «модель нарисована в меню/инвентаре» — ставится в миксине InventoryScreen. */
    public static final RenderStateDataKey<Boolean> GUI = RenderStateDataKey.create(() -> "mraz1visual:gui");
    /** Модель игрока уменьшена до 0.9375 — возвращаем единицу «блок», чтобы размеры совпадали с миром. */
    private static final float K = 1f / 0.9375f;

    public PlayerLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new PlayerLayer(avatar));
        });
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState s, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || s.id != mc.player.getId() || s.isInvisible || s.isSpectator) return;
        boolean gui = Boolean.TRUE.equals(s.getData(GUI));
        if (Wings3D.active()) {
            // к телу: поворот и наклон корпуса берутся из позы модели; оси → вправо +X, вверх +Y, вперёд −Z
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            ps.scale(-K, -K, K);
            Wings3D.submit(ps, c, gui);
            ps.popPose();
        }
        if (gui) {
            ps.pushPose();
            ps.scale(-K, -K, K);
            Pets.submitPreview(ps, c);
            ps.popPose();
        }
    }
}
