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
    final BoolSetting back = add(new BoolSetting("back", "Предмет на спине", true));
    final SliderSetting wingSize = add(new SliderSetting("wing_size", "Размер крыльев", 1, 0.5, 1.8, 0.05, "×"));
    final SliderSetting wingFlap = add(new SliderSetting("wing_flap", "Покачивание крыльев", 40, 0, 100, 5, " %"));
    final ActionSetting gallery = add(new ActionSetting("gallery", "Выбрать модели", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.open(dev.mraz1visual.ui.ModelGallery.Tab.COSMETICS)));

    private static ModelCosmetics instance;
    private static final java.util.Map<String, dev.mraz1visual.models.AnimPlayer> animationPlayers = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> animationTimes = new java.util.HashMap<>();

    private static float[] pose(ModelEntry e) {
        if (e.anims.isEmpty()) return null;
        var player = animationPlayers.computeIfAbsent(e.id, id -> new dev.mraz1visual.models.AnimPlayer());
        var clip = e.anim("idle");
        var wearer = net.minecraft.client.Minecraft.getInstance().player;
        if (e.collection.equals("pulse") && e.kind == ModelEntry.Kind.WING && wearer != null) {
            String movement = wearer.isFallFlying() ? "elytra" : wearer.isSwimming() ? "swimming"
                    : wearer.getDeltaMovement().horizontalDistanceSqr() > .001 ? "move" : "idle";
            String variant = wearer.isCrouching() ? ".sneak" : wearer.isInWater() ? ".water" : "";
            clip = e.anim(movement + variant);
            if (clip == null) clip = e.anim(movement);
        }
        player.play(clip != null ? clip : e.anims.getFirst(), .18f, false);
        long now = System.nanoTime();
        Long previous = animationTimes.put(e.id, now);
        if (previous != null && !PhotoMode.frozen()) player.update(Math.min(0.1f, (now - previous) / 1e9f));
        return player.pose(e.mesh());
    }

    public ModelCosmetics() {
        super("model_cosmetics", "Model Cosmetics", "Шляпа и крылья из своих моделей (из папки weapon_skins). Видны только тебе.", Category.COSMETICS);
        instance = this;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> clearAnimations());
    }

    private static void clearAnimations() {
        animationPlayers.clear();
        animationTimes.clear();
    }

    @Override protected void onDisable() { clearAnimations(); }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Only the selected model; an empty selection means that slot is unequipped. */
    public static ModelEntry pick(ModelEntry.Kind kind) {
        String id = kind == ModelEntry.Kind.HAT ? ModelPrefs.hat : kind == ModelEntry.Kind.BACK ? ModelPrefs.back : ModelPrefs.wing;
        if (id.isEmpty()) return null;
        ModelEntry e = ModelRegistry.get(id);
        if (e != null && !e.broken() && e.kind == kind) return e;
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
                if (e.auraPlacement != null) {
                    ps.scale(-1,-1,1);
                    e.auraPlacement.apply(ps,m.hatSize.getF());
                } else {
                // как предмет на голове у ванили (CustomHeadLayer), дальше — поза «head» из display модели
                ps.translate(0, -0.25f, 0);
                ps.mulPose(Axis.YP.rotationDegrees(180));
                float s = 0.625f * m.hatSize.getF();
                ps.scale(s, -s, -s);
                e.display[ItemDisplayContext.HEAD.ordinal()].apply(false, ps.last());
                }
                ModelDraw.submit(c, ps, e.mesh(), pose(e), -1, OverlayTexture.NO_OVERLAY, light, true);
                ps.popPose();
            }
        }
        if (m.wing.get() && !ShardWings.active()) {
            ModelEntry e = pick(ModelEntry.Kind.WING);
            if (e != null) {
                Mesh w = e.mesh();
                ps.pushPose();
                model.body.translateAndRotate(ps);
                ps.scale(-1, -1, 1); // вправо +X, вверх +Y, вперёд −Z
                if (e.auraPlacement != null) {
                    e.auraPlacement.apply(ps,m.wingSize.getF());
                } else {
                ps.translate(0, -0.3f, 0.18f);
                float flap = m.wingFlap.getF() / 100f * (float) Math.sin(dev.mraz1visual.render.Fx.time() * 2.2) * 4f;
                ps.mulPose(Axis.XP.rotationDegrees(-8 + flap));
                // крылья в модели разложены вдоль Z — разворот поперёк спины
                if (!e.collection.equals("aura")) ps.mulPose(Axis.YP.rotationDegrees(90));
                float span = Math.max(0.1f, Math.max(w.sizeZ(), w.sizeX()));
                float s = 2.2f * m.wingSize.getF() / span;
                ps.scale(s, s, s);
                ps.translate(-(w.bounds[0] + w.bounds[3]) / 2f, -(w.bounds[1] + w.bounds[4]) / 2f, -(w.bounds[2] + w.bounds[5]) / 2f);
                }
                ModelDraw.submit(c, ps, w, pose(e), -1, OverlayTexture.NO_OVERLAY, light, true);
                ps.popPose();
            }
        }
        if (m.back.get() && !ModelPrefs.back.isEmpty()) {
            ModelEntry e = pick(ModelEntry.Kind.BACK);
            if (e != null) {
                Mesh mesh = e.mesh();
                ps.pushPose();
                model.body.translateAndRotate(ps);
                ps.scale(-1, -1, 1);
                if (e.auraPlacement != null) {
                    e.auraPlacement.apply(ps,1);
                } else {
                ps.translate(0, -0.35f, 0.22f);
                float s = 0.8f / Math.max(0.1f, mesh.sizeY());
                ps.scale(s, s, s);
                ps.translate(-(mesh.bounds[0] + mesh.bounds[3]) / 2f, -(mesh.bounds[1] + mesh.bounds[4]) / 2f, -mesh.bounds[2]);
                }
                ModelDraw.submit(c, ps, mesh, pose(e), -1, OverlayTexture.NO_OVERLAY, light, true);
                ps.popPose();
            }
        }
    }
}
