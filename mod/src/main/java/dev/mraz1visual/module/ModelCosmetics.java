package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.models.Mesh;
import dev.mraz1visual.models.ModelDraw;
import dev.mraz1visual.models.ModelEntry;
import dev.mraz1visual.models.ModelPrefs;
import dev.mraz1visual.models.ModelRegistry;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Косметика из импортированных моделей: шляпа на голову и крылья на спину (например, hat и wing из
 * Carnivoret Weapon Set). Рисуется слоем модели игрока — видна от 3-го лица, в инвентаре и в превью.
 */
public final class ModelCosmetics extends Module {
    final BoolSetting hat = add(new BoolSetting("hat", "Шляпа", true));
    final SliderSetting hatSize = add(new SliderSetting("hat_size", "Размер шляпы", 1, 0.5, 1.6, 0.05, "×"));
    final BoolSetting wing = add(new BoolSetting("wing", "Крылья на спине", true));
    final SliderSetting wingSize = add(new SliderSetting("wing_size", "Размер крыльев", 1, 0.5, 1.8, 0.05, "×"));
    final SliderSetting wingFlap = add(new SliderSetting("wing_flap", "Покачивание крыльев", 40, 0, 100, 5, " %"));
    final ActionSetting gallery = add(new ActionSetting("gallery", "Выбрать модели", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.open(dev.mraz1visual.ui.ModelGallery.Tab.COSMETICS)));

    private static ModelCosmetics instance;

    public ModelCosmetics() {
        super("model_cosmetics", "Model Cosmetics", "Шляпа и крылья из своих моделей (из папки weapon_skins). Видны только тебе.", Category.COSMETICS);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Модель выбранной шляпы/крыльев, иначе — первая подходящая из папки. */
    public static ModelEntry pick(ModelEntry.Kind kind) {
        ModelEntry e = ModelRegistry.get(kind == ModelEntry.Kind.HAT ? ModelPrefs.hat : ModelPrefs.wing);
        if (e != null && !e.broken() && e.kind == kind) return e;
        for (ModelEntry x : ModelRegistry.of(kind)) if (!x.broken()) return x;
        return null;
    }

    /** Из слоя модели игрока (поза — корень модели, до переворота осей). */
    public static void submit(PlayerModel model, PoseStack ps, SubmitNodeCollector c, int light, boolean gui) {
        ModelCosmetics m = instance;
        if (m == null || !m.isEnabled()) return;
        if (m.hat.get()) {
            ModelEntry e = pick(ModelEntry.Kind.HAT);
            if (e != null) {
                ps.pushPose();
                model.head.translateAndRotate(ps);
                // как предмет на голове у ванили (CustomHeadLayer), дальше — поза «head» из display модели
                ps.translate(0, -0.25f, 0);
                ps.mulPose(Axis.YP.rotationDegrees(180));
                float s = 0.625f * m.hatSize.getF();
                ps.scale(s, -s, -s);
                e.display[ItemDisplayContext.HEAD.ordinal()].apply(false, ps.last());
                ModelDraw.submit(c, ps, e.mesh(), null, -1, OverlayTexture.NO_OVERLAY, light, true);
                ps.popPose();
            }
        }
        if (m.wing.get()) {
            ModelEntry e = pick(ModelEntry.Kind.WING);
            if (e != null) {
                Mesh w = e.mesh();
                ps.pushPose();
                model.body.translateAndRotate(ps);
                ps.scale(-1, -1, 1); // вправо +X, вверх +Y, вперёд −Z
                ps.translate(0, -0.3f, 0.18f);
                float flap = m.wingFlap.getF() / 100f * (float) Math.sin(System.nanoTime() / 1e9 * 2.2) * 4f;
                ps.mulPose(Axis.XP.rotationDegrees(-8 + flap));
                // крылья в модели разложены вдоль Z — разворот поперёк спины
                ps.mulPose(Axis.YP.rotationDegrees(90));
                float span = Math.max(0.1f, Math.max(w.sizeZ(), w.sizeX()));
                float s = 2.2f * m.wingSize.getF() / span;
                ps.scale(s, s, s);
                ps.translate(-(w.bounds[0] + w.bounds[3]) / 2f, -(w.bounds[1] + w.bounds[4]) / 2f, -(w.bounds[2] + w.bounds[5]) / 2f);
                ModelDraw.submit(c, ps, w, null, -1, OverlayTexture.NO_OVERLAY, light, true);
                ps.popPose();
            }
        }
    }
}
