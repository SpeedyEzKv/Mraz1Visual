package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.BodyFrame;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * «Огненная рука»: предмет в руке (или сама рука) в мягком анимированном пламени с искрами.
 * Пламя — своя система частиц с аддитивным свечением; от 1-го лица привязано к предмету,
 * от 3-го — к руке персонажа. Всё только у тебя на экране.
 */
public final class FireHand extends Module implements WorldLayer, AttackListener {
    private static FireHand instance;
    private static final int MAX = 160;

    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0,
            "fire", "Огонь", "soul", "Синий (души)", "purple", "Фиолетовый", "rainbow", "Радужный", "custom", "Свои цвета"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Режим цвета (свои)", 1, "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1 (свои)", 0xFFFF7A1A));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2 (свои)", 0xFFFFD27A));
    final EnumSetting when = add(new EnumSetting("when", "Когда", 0, "always", "Всегда", "hit", "Только при ударе"));
    final SliderSetting intensity = add(new SliderSetting("intensity", "Интенсивность", 1, 0.2, 2, 0.05, "×"));
    final SliderSetting sparks = add(new SliderSetting("sparks", "Искры", 10, 0, 40, 1, ""));
    final SliderSetting size = add(new SliderSetting("size", "Размер пламени", 1, 0.5, 2, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.3, 3, 0.05, "×"));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 85, 20, 100, 5, " %"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 85, 10, 100, 5, " %"));

    // пул частиц: смещение от точки привязки (в блоках), скорость, возраст, срок, размер, тип (0 — пламя, 1 — искра)
    private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], sz = new float[MAX], seed = new float[MAX];
    private final boolean[] spark = new boolean[MAX];
    private int count;
    private float spawnAcc, sparkAcc;
    private long last;
    private long hitAt;
    private float activeK;
    private final java.util.Random rnd = new java.util.Random();
    private final BodyFrame frame = new BodyFrame();

    // буферы для 1-го лица (без аллокаций в кадре)
    private final Matrix4f inv = new Matrix4f();
    private final Vector3f tmp = new Vector3f();

    public FireHand() {
        super("fire_hand", "Fire Hand", "Предмет и рука в анимированном огне с искрами.", Category.SWORDS);
        instance = this;
    }

    @Override
    public void onAttack(Entity e) { hitAt = System.currentTimeMillis(); }

    // ── цвета ────────────────────────────────────────────────

    private int hot() {
        return switch (style.getId()) {
            case "soul" -> 0xFFB8F4FF;
            case "purple" -> 0xFFF0D0FF;
            default -> 0xFFFFF4C8;
        };
    }

    private int mid(float t) {
        return switch (style.getId()) {
            case "soul" -> 0xFF35C8F0;
            case "purple" -> 0xFFB04CFF;
            case "rainbow" -> ColorFx.pick("rainbow", 0xFFFFFFFF, 0xFFFFFFFF, t);
            case "custom" -> ColorFx.pick(colorMode.getId(), color.get(), color2.get(), t);
            default -> 0xFFFF7A1A;
        };
    }

    private int cool(float t) {
        return switch (style.getId()) {
            case "soul" -> 0xFF1E5FD0;
            case "purple" -> 0xFF5A1FB0;
            case "rainbow" -> ColorFx.pick("rainbow", 0xFFFFFFFF, 0xFFFFFFFF, t + 0.3f);
            case "custom" -> Colors.shade(ColorFx.pick(colorMode.getId(), color2.get(), color.get(), t), 0.8f);
            default -> 0xFFD0301A;
        };
    }

    /** Цвет частицы по доле жизни: горячее ядро → основной → холодный край, с учётом яркости и прозрачности. */
    private int colorAt(int i, float f) {
        int c = f < 0.25f ? Colors.lerp(hot(), mid(seed[i]), f / 0.25f) : Colors.lerp(mid(seed[i]), cool(seed[i]), (f - 0.25f) / 0.75f);
        c = Colors.shade(c, brightness.getF() / 100f);
        float a = (1f - f) * (f < 0.1f ? f / 0.1f : 1f) * opacity.getF() / 100f * activeK;
        return Colors.withAlpha(c, Math.round(255 * a * (spark[i] ? 1f : 0.55f)));
    }

    // ── симуляция (раз в кадр, по реальному времени) ────────

    private void simulate() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        boolean on = when.getId().equals("always") || System.currentTimeMillis() - hitAt < 1500;
        activeK += ((on ? 1f : 0f) - activeK) * Math.min(1f, dt * 6f);
        float sp = speed.getF();
        float s = size.getF();
        if (activeK > 0.02f) {
            spawnAcc += dt * 70f * intensity.getF() * activeK;
            sparkAcc += dt * sparks.getF() * activeK;
        }
        while (spawnAcc >= 1f) { spawnAcc -= 1f; spawn(false, s); }
        while (sparkAcc >= 1f) { sparkAcc -= 1f; spawn(true, s); }
        for (int i = 0; i < count; i++) {
            age[i] += dt * sp;
            if (age[i] >= life[i]) {
                kill(i);
                i--;
                continue;
            }
            // пламя тянется вверх и чуть закручивается; искры летят и замедляются
            float wob = (float) Math.sin((age[i] * 9f + seed[i] * 20f)) * 0.04f * s;
            px[i] += (vx[i] + (spark[i] ? 0 : wob)) * dt * sp;
            py[i] += vy[i] * dt * sp;
            pz[i] += (vz[i] + (spark[i] ? 0 : wob * 0.6f)) * dt * sp;
            if (spark[i]) { vx[i] *= 0.97f; vz[i] *= 0.97f; vy[i] -= 0.1f * dt; }
        }
    }

    private void spawn(boolean isSpark, float s) {
        if (count >= MAX) return;
        int i = count++;
        spark[i] = isSpark;
        seed[i] = rnd.nextFloat();
        float r = 0.06f * s;
        px[i] = (rnd.nextFloat() - 0.5f) * r;
        py[i] = (rnd.nextFloat() - 0.5f) * r * 2f;
        pz[i] = (rnd.nextFloat() - 0.5f) * r;
        if (isSpark) {
            vx[i] = (rnd.nextFloat() - 0.5f) * 0.5f * s;
            vy[i] = (0.25f + rnd.nextFloat() * 0.5f) * s;
            vz[i] = (rnd.nextFloat() - 0.5f) * 0.5f * s;
            life[i] = 0.6f + rnd.nextFloat() * 0.6f;
            sz[i] = 0.008f * s;
        } else {
            vx[i] = (rnd.nextFloat() - 0.5f) * 0.05f;
            vy[i] = (0.18f + rnd.nextFloat() * 0.18f) * s;
            vz[i] = (rnd.nextFloat() - 0.5f) * 0.05f;
            life[i] = 0.35f + rnd.nextFloat() * 0.35f;
            sz[i] = (0.035f + rnd.nextFloat() * 0.03f) * s;
        }
        age[i] = 0;
    }

    private void kill(int i) {
        int j = --count;
        px[i] = px[j]; py[i] = py[j]; pz[i] = pz[j];
        vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        age[i] = age[j]; life[i] = life[j]; sz[i] = sz[j]; seed[i] = seed[j]; spark[i] = spark[j];
    }

    // ── 3-е лицо: у руки персонажа ───────────────────────────

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        simulate();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || count == 0 || mc.options.getCameraType().isFirstPerson()) return;
        frame.body(p, pt);
        // правая кисть: чуть правее и ниже плеч, немного вперёд при взмахе
        float swing = p.getAttackAnim(pt);
        double hx = frame.wx(-0.36, -0.62 + swing * 0.3, 0.12 + swing * 0.35);
        double hy = frame.wy(-0.36, -0.62 + swing * 0.3, 0.12 + swing * 0.35);
        double hz = frame.wz(-0.36, -0.62 + swing * 0.3, 0.12 + swing * 0.35);
        d.glowBegin();
        for (int i = 0; i < count; i++) {
            float f = age[i] / life[i];
            float r = sz[i] * (spark[i] ? 1f : (1f - f * 0.6f)) * 2.2f;
            d.billboardDot(hx + px[i] * 2.2, hy + py[i] * 2.2, hz + pz[i] * 2.2, r, colorAt(i, f), spark[i] ? 6 : 10);
        }
        d.glowEnd();
    }

    // ── 1-е лицо: на предмете (миксин ItemInHandRenderer.renderItem) ──

    public static void renderItem(LivingEntity e, ItemStack stack, ItemDisplayContext ctx, PoseStack ps, SubmitNodeCollector c) {
        FireHand f = instance;
        if (f == null || !f.isEnabled() || f.count == 0 || e != Minecraft.getInstance().player) return;
        if (ctx != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ps.pushPose();
        dev.mraz1visual.render.HandFx.handheld(ps, left);
        f.submit(ps, c, 0.5f, 0.62f, 0.5f, 0.16f);
        ps.popPose();
    }

    /** Пустая рука от 1-го лица (миксин AvatarRenderer.renderHand): огонь у кулака. */
    public static void renderFist(PoseStack ps, SubmitNodeCollector c, net.minecraft.client.model.geom.ModelPart arm) {
        FireHand f = instance;
        if (f == null || !f.isEnabled() || f.count == 0) return;
        ps.pushPose();
        arm.translateAndRotate(ps);
        f.submit(ps, c, arm.x < 0 ? -1 / 16f : 1 / 16f, 10 / 16f, 0f, 0.12f);
        ps.popPose();
    }

    /**
     * Частицы как «спрайты» к камере в пространстве вида: точка привязки переводится в координаты вида,
     * спрайты строятся там (вверх = вверх экрана) и возвращаются обратной матрицей.
     */
    private void submit(PoseStack ps, SubmitNodeCollector c, float ax, float ay, float az, float spread) {
        final float sx = ax, sy = ay, sz0 = az, k = spread / 0.06f;
        c.submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
            Matrix4f m = pose.pose();
            m.transformPosition(sx, sy, sz0, tmp);
            float cx = tmp.x, cy = tmp.y, cz = tmp.z;
            inv.set(m).invert();
            curPose = m;
            for (int i = 0; i < count; i++) {
                float fr = age[i] / life[i];
                float r = sz[i] * (spark[i] ? 1f : (1f - fr * 0.6f)) * 1.6f;
                int col = colorAt(i, fr);
                float x = cx + px[i] * k, y = cy + py[i] * k * 1.4f, z = cz + pz[i] * k;
                sprite(vc, x, y, z, r, col);
            }
        });
    }

    /** Ромб-«огонёк» в плоскости экрана: центр яркий, края прозрачные (2 треугольника на сторону). */
    private void sprite(VertexConsumer vc, float x, float y, float z, float r, int col) {
        int edge = col & 0x00FFFFFF;
        corner(vc, x, y, z, col); corner(vc, x + r, y, z, edge); corner(vc, x, y + r * 1.4f, z, edge); corner(vc, x, y, z, col);
        corner(vc, x, y, z, col); corner(vc, x, y + r * 1.4f, z, edge); corner(vc, x - r, y, z, edge); corner(vc, x, y, z, col);
        corner(vc, x, y, z, col); corner(vc, x - r, y, z, edge); corner(vc, x, y - r * 0.8f, z, edge); corner(vc, x, y, z, col);
        corner(vc, x, y, z, col); corner(vc, x, y - r * 0.8f, z, edge); corner(vc, x + r, y, z, edge); corner(vc, x, y, z, col);
    }

    /** Вершина в пространстве вида → через обратную матрицу в локальное, чтобы pose вернул её на место. */
    private void corner(VertexConsumer vc, float x, float y, float z, int col) {
        inv.transformPosition(x, y, z, tmp);
        vc.addVertex(curPose, tmp.x, tmp.y, tmp.z).setColor(col);
    }

    private Matrix4f curPose = new Matrix4f();
}
