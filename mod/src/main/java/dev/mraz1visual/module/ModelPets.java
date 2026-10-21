package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.models.Anim;
import dev.mraz1visual.models.AnimPlayer;
import dev.mraz1visual.models.Mesh;
import dev.mraz1visual.models.ModelDraw;
import dev.mraz1visual.models.ModelEntry;
import dev.mraz1visual.models.ModelPrefs;
import dev.mraz1visual.models.ModelRegistry;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Питомцы из импортированных моделей Blockbench (папка mraz1visual/pets): ходят или парят рядом,
 * следуют пружиной, обходят блоки, мягко появляются после телепорта. Анимации idle / walk (fly),
 * особые (special, speacial, attack, shoot, pet) раз в 15–40 с, переходы — плавным смешиванием.
 * Не сущность и не пакет: только картинка на твоём экране.
 */
public final class ModelPets extends Module implements WorldLayer {
    final EnumSetting side = add(new EnumSetting("side", "Где", 0, "right", "Справа", "left", "Слева", "back", "Сзади", "shoulder", "На плече"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.3, 2.5, 0.05, "×"));
    final SliderSetting follow = add(new SliderSetting("follow", "Скорость следования", 1, 0.4, 2.5, 0.05, "×"));
    final SliderSetting animSpeed = add(new SliderSetting("anim_speed", "Скорость анимации", 1, 0.3, 2.5, 0.05, "×"));
    final BoolSetting firstPerson = add(new BoolSetting("first_person", "Показывать в 1-м лице", true));
    final BoolSetting nameTag = add(new BoolSetting("name_tag", "Табличка с именем", false));
    final BoolSetting specials = add(new BoolSetting("specials", "Особые анимации", true).hint("Раз в 15–40 секунд питомец делает что-нибудь своё."));
    final BoolSetting glow = add(new BoolSetting("glow", "Свечение", true));
    final ActionSetting gallery = add(new ActionSetting("gallery", "Выбрать питомца", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.open(dev.mraz1visual.ui.ModelGallery.Tab.PETS)));
    final ActionSetting reload = add(new ActionSetting("reload", "Папка pets", "Обновить", ModelRegistry::reload));
    final ActionSetting folder = add(new ActionSetting("folder", "Открыть папку", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.openFolder(ModelRegistry.PETS)));

    private static ModelPets instance;

    private ModelEntry pet;
    private final AnimPlayer anim = new AnimPlayer();
    private double x, y, z, vx, vy, vz, yaw;
    private boolean placed;
    private long last;
    private float moving, appear, nextSpecial = -1, clock;
    private boolean inSpecial;
    private String forced;
    private Component label;
    private ModelEntry fx;
    private final AnimPlayer fxAnim = new AnimPlayer();
    private double fxX, fxY, fxZ, fxYaw;
    private final PoseStack ps = new PoseStack();
    private final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

    public ModelPets() {
        super("model_pets", "Model Pets", "Питомцы из своих моделей Blockbench (папка mraz1visual/pets). Видны только тебе.", Category.PETS);
        instance = this;
    }

    public static ModelPets get() {
        return instance;
    }

    @Override
    protected void onDisable() {
        placed = false;
    }

    /** Выбрать питомца (из галереи). */
    public void select(ModelEntry e) {
        ModelPrefs.pet = e == null ? "" : e.id;
        ModelPrefs.save();
        pet = null;
        placed = false;
    }

    /** Проиграть анимацию по кнопке в галерее. */
    public void playNow(String name) {
        forced = name;
    }

    private ModelEntry current() {
        if (pet == null || ModelRegistry.get(pet.id) != pet) {
            pet = ModelRegistry.get(ModelPrefs.pet);
            if (pet == null || pet.broken()) {
                // ничего не выбрано — первый рабочий из папки
                pet = null;
                for (ModelEntry e : ModelRegistry.of(ModelEntry.Kind.PET)) if (!e.broken()) { pet = e; break; }
            }
            anim.play(null, 0, true);
            inSpecial = false;
            label = pet == null ? null : Component.literal(pet.name);
        }
        return pet;
    }

    // ── анимации ─────────────────────────────────────────────

    static boolean isSpecial(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.contains("special") || n.contains("speacial") || n.contains("attack") || n.contains("shoot") || n.equals("pet");
    }

    private Anim base(ModelEntry e) {
        Anim a = null;
        if (moving > 0.5f) {
            if (e.flying) a = e.anim("fly");
            if (a == null) a = e.anim("walk");
            if (a == null) a = e.anim("move");
        }
        if (a == null) a = e.anim("idle");
        if (a == null) a = e.anim("loop");
        if (a == null) for (Anim x : e.anims) if (x.loop == Anim.LOOP && !isSpecial(x.name)) { a = x; break; }
        return a;
    }

    private void updateAnim(ModelEntry e, float dt) {
        if (nextSpecial < 0) nextSpecial = clock + 15 + (float) Math.random() * 25;
        if (forced != null) {
            Anim f = e.anim(forced);
            forced = null;
            if (f != null) {
                anim.play(f, 0.2f, true);
                inSpecial = true;
                startFx(e, f);
            }
        } else if (inSpecial && (anim.finished() || anim.current() == null || anim.current().loop == Anim.LOOP && anim.time() > anim.current().length)) {
            inSpecial = false;
            nextSpecial = clock + 15 + (float) Math.random() * 25;
        } else if (!inSpecial && specials.get() && clock > nextSpecial && moving < 0.5f) {
            java.util.List<Anim> list = new java.util.ArrayList<>();
            for (Anim a : e.anims) if (isSpecial(a.name)) list.add(a);
            if (!list.isEmpty()) {
                Anim s = list.get((int) (Math.random() * list.size()));
                anim.play(s, 0.25f, true);
                inSpecial = true;
                startFx(e, s);
            } else {
                nextSpecial = clock + 15 + (float) Math.random() * 25;
            }
        }
        if (!inSpecial) anim.play(base(e), 0.3f, false);
        anim.update(dt * animSpeed.getF());
        if (fx != null) {
            fxAnim.update(dt * animSpeed.getF());
            if (fxAnim.current() == null || fxAnim.finished() || fxAnim.time() > 3f) fx = null;
        }
    }

    /** Эффект особой анимации (vfx_* из пака питомца) — на месте питомца, пока играет. */
    private void startFx(ModelEntry e, Anim special) {
        if (e.effects.isEmpty()) return;
        ModelEntry f = e.effects.get((int) (Math.random() * e.effects.size()));
        Anim a = f.anims.isEmpty() ? null : f.anims.get(0);
        for (Anim x : f.anims) if (x.name.equals("on") || x.name.equals("explosion") || x.name.equals("idle")) { a = x; break; }
        if (a == null) return;
        fx = f;
        fxAnim.play(a, 0, true);
        fxX = x; fxY = y; fxZ = z; fxYaw = yaw;
    }

    // ── движение ─────────────────────────────────────────────

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ModelEntry e = current();
        if (p == null || e == null || mc.level == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        clock += dt;
        Mesh m = e.mesh();
        float scale = scale(m);

        double ex = p.xo + (p.getX() - p.xo) * pt, ey = p.yo + (p.getY() - p.yo) * pt, ez = p.zo + (p.getZ() - p.zo) * pt;
        double pyaw = Math.toRadians(p.getYRot());
        double dirX = -Math.sin(pyaw), dirZ = Math.cos(pyaw), rx = -dirZ, rz = dirX;
        String where = side.getId();
        boolean shoulder = where.equals("shoulder");
        double off = switch (where) { case "left" -> -1; case "back" -> 0; case "shoulder" -> 0.42; default -> 1; };
        double back = where.equals("back") ? 1.3 : shoulder ? 0.05 : 0.55;
        double tx = ex + rx * off * (shoulder ? 1 : 1.1) - dirX * back, tz = ez + rz * off * (shoulder ? 1 : 1.1) - dirZ * back;
        double ty;
        boolean walker = !e.flying && !shoulder;
        if (shoulder) {
            ty = ey + (p.isCrouching() ? 1.15 : 1.45);
        } else if (walker) {
            ty = ground(mc, tx, ey + 0.6, tz, ey);
        } else {
            ty = ey + 1.25 + Math.sin(clock * 1.6) * 0.07;
            for (int k = 0; k < 3; k++) {
                bp.set(Math.floor(tx), Math.floor(ty + 0.3), Math.floor(tz));
                if (!mc.level.getBlockState(bp).isSolidRender()) break;
                ty += 1;
            }
        }
        if (!placed) {
            x = tx; y = ty; z = tz; yaw = pyaw;
            vx = vy = vz = 0;
            placed = true;
            appear = 0;
        }
        if (shoulder) {
            // на плече — без пружины, точно за игроком
            x = tx; y = ty; z = tz; vx = vy = vz = 0;
        } else {
            double k = follow.get(), stiff = 22 * k * k, damp = 8.5 * k;
            vx += ((tx - x) * stiff - vx * damp) * dt;
            vy += ((ty - y) * stiff * (walker ? 2 : 1) - vy * damp * (walker ? 1.4 : 1)) * dt;
            vz += ((tz - z) * stiff - vz * damp) * dt;
            x += vx * dt; y += vy * dt; z += vz * dt;
            if ((tx - x) * (tx - x) + (tz - z) * (tz - z) + (ty - y) * (ty - y) > 100) {
                // далеко отстал (телепорт, полёт) — появляется рядом мягко
                x = tx; y = ty; z = tz; vx = vy = vz = 0;
                appear = 0;
            }
        }
        appear = Math.min(1, appear + dt / 0.45f);
        double hv = Math.sqrt(vx * vx + vz * vz);
        moving += ((hv > 0.35 ? 1f : 0f) - moving) * Math.min(1f, dt * 6f);
        double want = shoulder || hv <= 0.35 ? pyaw : Math.atan2(-vx, vz);
        double dyaw = Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
        yaw += dyaw * Math.min(1, dt * 7);
        updateAnim(e, dt);

        if (!firstPerson.get() && mc.options.getCameraType() == CameraType.FIRST_PERSON) return;
        MultiBufferSource src = d.source();
        if (src == null) return;
        int light = LevelRenderer.getLightColor(mc.level, bp.set(Math.floor(x), Math.floor(y + 0.4), Math.floor(z)));
        float pop = easeBack(appear);
        drawWorld(d, src, m, anim.pose(m), x, y, z, yaw, scale * pop, light);
        if (fx != null) {
            Mesh fm = fx.mesh();
            drawWorld(d, src, fm, fxAnim.pose(fm), fxX, fxY, fxZ, fxYaw, scale, light);
        }
        d.restore();
        if (nameTag.get() && label != null) {
            d.label(label, x, y + m.sizeY() * scale + 0.25, z, 1f, 0xFFFFFFFF, 0x40000000);
        }
    }

    /** Высота земли под точкой: верх первого твёрдого блока вниз (до 6 блоков), иначе высота игрока. */
    private double ground(Minecraft mc, double x, double from, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(from);
        for (int yy = top; yy > top - 7; yy--) {
            bp.set(bx, yy, bz);
            var st = mc.level.getBlockState(bp);
            if (!st.getCollisionShape(mc.level, bp).isEmpty()) {
                double h = st.getCollisionShape(mc.level, bp).max(net.minecraft.core.Direction.Axis.Y);
                return yy + Math.min(1, h);
            }
        }
        return fallback;
    }

    private float scale(Mesh m) {
        float dim = Math.max(m.sizeY(), Math.max(m.sizeX(), m.sizeZ()));
        float target = side.getId().equals("shoulder") ? 0.38f : 0.62f;
        return target * size.getF() / Math.max(0.25f, dim);
    }

    private static float easeBack(float t) {
        float c = 1.6f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private void drawWorld(WorldDraw d, MultiBufferSource src, Mesh m, float[] pose, double wx, double wy, double wz, double yaw, float s, int light) {
        if (m == null || m.texture() == null || s <= 0.001f) return;
        m.atlas.lastUsed = System.currentTimeMillis();
        ps.setIdentity();
        ps.last().pose().set(d.framePose());
        ps.translate((float) (wx - d.camX()), (float) (wy - d.camY()), (float) (wz - d.camZ()));
        // модели Blockbench смотрят на север (−Z): разворот к направлению движения
        ps.mulPose(com.mojang.math.Axis.YP.rotation((float) (-yaw + Math.PI)));
        ps.scale(s, s, s);
        if (m.bones.length > 0) ModelDraw.bones(m, pose);
        VertexConsumer vc = src.getBuffer(RenderTypes.entityCutoutNoCull(m.texture()));
        ModelDraw.draw(m, ps.last(), vc, -1, OverlayTexture.NO_OVERLAY, light, ModelDraw.SOLID);
        if (glow.get() && m.hasGlow) {
            if (m.selfGlow) ModelDraw.draw(m, ps.last(), src.getBuffer(RenderTypes.eyes(m.texture())), -1, OverlayTexture.NO_OVERLAY, light, ModelDraw.GLOW_SELF);
            if (m.atlas.glowTexture != null) ModelDraw.draw(m, ps.last(), src.getBuffer(RenderTypes.eyes(m.atlas.glowTexture)), -1, OverlayTexture.NO_OVERLAY, light, ModelDraw.GLOW_LAYER);
        }
    }

    // ── превью в меню: у правого плеча модели игрока ─────────

    public static void submitPreview(PoseStack ps, SubmitNodeCollector c) {
        ModelPets mp = instance;
        if (mp == null || !mp.isEnabled()) return;
        ModelEntry e = mp.current();
        if (e == null) return;
        if (!mp.placed) {
            long now = System.nanoTime();
            float dt = mp.last == 0 ? 0 : Math.min(0.05f, (now - mp.last) / 1e9f);
            mp.last = now;
            mp.clock += dt;
            mp.updateAnim(e, dt);
        }
        Mesh m = e.mesh();
        float s = mp.scale(m) * 0.8f;
        ps.pushPose();
        ps.translate(0.55f, 0.15f + (float) Math.sin(mp.clock * 1.6) * 0.03f, -0.1f);
        // лицом к зрителю (модель смотрит на −Z, как и игрок), чуть вполоборота
        ps.mulPose(com.mojang.math.Axis.YP.rotation(0.45f));
        ps.scale(s, s, s);
        ModelDraw.submit(c, ps, m, mp.anim.pose(m), -1, OverlayTexture.NO_OVERLAY, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, mp.glow.get());
        ps.popPose();
    }
}
