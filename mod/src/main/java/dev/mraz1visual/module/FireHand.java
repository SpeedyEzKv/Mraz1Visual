package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.ItemFx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Аура предмета»: мягкое светящееся пламя-сияние, которое обтекает силуэт предмета в руке.
 * Точки силуэта берутся из самой модели (рёбра спрайта), сияние держится по всему контуру и тихо «дышит»,
 * языки света медленно отрываются вверх и растворяются (плавный шум вместо мерцания), редкие искры поднимаются
 * и гаснут, при ударе — короткая вспышка с мягким затуханием. Всё в пространстве модели предмета: от 1-го и 3-го лица
 * и в превью аура сидит ровно на предмете, не отстаёт при взмахе и покачивании камеры.
 */
public final class FireHand extends Module implements AttackListener {
    private static FireHand instance;
    private static final int MAX = 220;

    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS)
            .alias("fire", "sunset").alias("soul", "ether").alias("purple", "amethyst").alias("rainbow", "chrome"));
    final ColorSetting color = add(new ColorSetting("color", "Центр (свой)", 0xFFFFE3A8));
    final ColorSetting color2 = add(new ColorSetting("color2", "Край (свой)", 0xFFFF6A8A));
    final EnumSetting when = add(new EnumSetting("when", "Когда", 0, "always", "Всегда", "weapon", "Только с оружием", "hit", "Только при ударе"));
    final SliderSetting intensity = add(new SliderSetting("intensity", "Интенсивность", 1, 0.2, 2, 0.05, "×"));
    final SliderSetting sparks = add(new SliderSetting("sparks", "Искры", 8, 0, 30, 1, ""));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость", 1, 0.3, 3, 0.05, "×"));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 80, 20, 100, 5, " %"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 85, 10, 100, 5, " %"));
    final EnumSetting quality = add(new EnumSetting("quality", "Качество", 1, "low", "Низкое", "medium", "Среднее", "high", "Высокое"));

    // частицы в пространстве модели, в долях размера модели S: позиция, скорость, возраст, срок, размер, зерно, искра?
    private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], sz = new float[MAX], seed = new float[MAX];
    private final boolean[] spark = new boolean[MAX];
    private int count;
    private float spawnAcc, sparkAcc, activeK, flash, time;
    private long last, hitAt, simulatedAt;
    private final java.util.Random rnd = new java.util.Random();

    // силуэт текущей модели (центры рёбер и нормали наружу), в долях от начала модели и S
    private static final Map<List<BakedQuad>, float[]> OUTLINES = new IdentityHashMap<>();
    /** Контур своего клинка: точки по обе стороны диагонали (в долях модели) и нормали наружу. */
    private static final float[] BLADE = new float[32 * 4];
    static {
        float n = 0.70710678f;
        for (int i = 0; i < 16; i++) {
            float t = 0.36f + 0.62f * i / 15f;
            float w = 0.05f * (t > 0.9f ? (1f - t) / 0.1f : 1f);
            for (int side = 0; side < 2; side++) {
                int k = (i * 2 + side) * 4;
                float sg = side == 0 ? 1 : -1;
                BLADE[k] = t + (-n) * w * sg;
                BLADE[k + 1] = t + n * w * sg;
                BLADE[k + 2] = -n * sg;
                BLADE[k + 3] = n * sg;
            }
        }
    }
    private float[] outline = new float[0];
    private final float[] box = new float[6];
    private float bx0, by0, bz, bS;
    // «вверх» для языков пламени в пространстве модели (экранный верх от 1-го лица, мировой — от 3-го)
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Matrix3f invNormal = new Matrix3f();
    private final Matrix4f inv = new Matrix4f();
    private final Vector3f tmp = new Vector3f(), right = new Vector3f(), upv = new Vector3f();
    private Matrix4f curPose = new Matrix4f();

    public FireHand() {
        super("fire_hand", "Item Aura", "Мягкое сияние по контуру предмета в руке: языки света, искры, вспышка при ударе.", Category.SWORDS);
        instance = this;
    }

    @Override
    public void onAttack(Entity e) {
        hitAt = System.currentTimeMillis();
        flash = 1f;
    }

    // ── вход из ItemFx ───────────────────────────────────────

    public static void submitAura(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, List<BakedQuad> quads, boolean sword, boolean gui) {
        FireHand f = instance;
        if (f == null || !f.isEnabled() || quads == null || quads.isEmpty()) return;
        boolean first = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (f.when.getId().equals("weapon") && !ItemFx.currentIsWeapon()) return;
        if (!ItemFx.bounds(quads, f.box)) return;
        f.useOutline(quads);
        // меч заменён своим — контур ауры по его клинку (диагональ модели), а не по ванильному спрайту
        if (sword && CustomSwords.replacing()) f.outline = BLADE;
        f.finish(ps, c, first, gui);
    }

    /** Аура вокруг импортированного скина: контур — по рамке его модели (в пространстве модели предмета). */
    public static void submitAuraBounds(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, float[] bounds, boolean gui) {
        FireHand f = instance;
        if (f == null || !f.isEnabled()) return;
        if (f.when.getId().equals("weapon") && !ItemFx.currentIsWeapon()) return;
        System.arraycopy(bounds, 0, f.box, 0, 6);
        f.bx0 = bounds[0]; f.by0 = bounds[1]; f.bz = (bounds[2] + bounds[5]) / 2f;
        f.bS = Math.max(1e-4f, Math.max(bounds[3] - bounds[0], bounds[4] - bounds[1]));
        f.outline = RING;
        f.finish(ps, c, ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND, gui);
    }

    private void finish(PoseStack ps, SubmitNodeCollector c, boolean first, boolean gui) {
        FireHand f = this;
        simulate(ps.last().normal(), first, gui);
        if (count == 0 && activeK < 0.02f) return;
        // оси экрана для «спрайтов» — в пространстве, куда поза переводит модель
        boolean camAxes = !first && !gui;
        c.submitCustomGeometry(ps, dev.mraz1visual.render.FxVertex.glow(), (pose, vc) -> f.draw(vc, pose.pose(), camAxes, gui));
    }

    /** Точки по периметру рамки модели (для 3D-моделей без рёбер спрайта). */
    private static final float[] RING = new float[24 * 4];

    static {
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            RING[i * 4] = 0.5f + 0.5f * (float) Math.cos(a); RING[i * 4 + 1] = 0.5f + 0.5f * (float) Math.sin(a);
            RING[i * 4 + 2] = (float) Math.cos(a); RING[i * 4 + 3] = (float) Math.sin(a);
        }
    }

    private void useOutline(List<BakedQuad> quads) {
        bx0 = box[0]; by0 = box[1]; bz = (box[2] + box[5]) / 2f;
        bS = Math.max(1e-4f, Math.max(box[3] - box[0], box[4] - box[1]));
        float[] o = OUTLINES.get(quads);
        if (o == null) {
            if (OUTLINES.size() > 64) OUTLINES.clear();
            o = extract(quads);
            OUTLINES.put(quads, o);
        }
        outline = o;
    }

    /** Центры рёбер спрайта (грани вверх/вниз/вбок у сгенерированной модели) и нормали наружу; нет рёбер — периметр рамки. */
    private float[] extract(List<BakedQuad> quads) {
        int n = 0;
        for (BakedQuad q : quads) if (q.direction() != Direction.NORTH && q.direction() != Direction.SOUTH) n++;
        if (n < 6) return RING; // 3D-модель без рёбер спрайта — точки по периметру рамки
        float[] o = new float[n * 4];
        int k = 0;
        for (BakedQuad q : quads) {
            Direction d = q.direction();
            if (d == Direction.NORTH || d == Direction.SOUTH) continue;
            float cx = 0, cy = 0;
            for (int i = 0; i < 4; i++) { cx += q.position(i).x(); cy += q.position(i).y(); }
            cx /= 4; cy /= 4;
            o[k * 4] = (cx - box[0]) / bS; o[k * 4 + 1] = (cy - box[1]) / bS;
            o[k * 4 + 2] = d.getStepX(); o[k * 4 + 3] = d.getStepY();
            k++;
        }
        return o;
    }

    // ── цвета ────────────────────────────────────────────────

    /** Цвет по доле жизни f (0 — рождение у контура, 1 — растворение), с яркостью и прозрачностью. */
    private int colorAt(float f, float sd, float alpha) {
        int c = Palette.color(preset.getId(), 0.08f + f * 0.92f, time * 0.4f + sd, color.get(), color2.get());
        c = Colors.shade(c, brightness.getF() / 100f * (1f + 0.6f * flash));
        float a = alpha * opacity.getF() / 100f * activeK;
        return Colors.withAlpha(c, Math.max(0, Math.min(255, Math.round(255 * a))));
    }

    // ── симуляция (раз в кадр, по реальному времени) ────────

    private void simulate(Matrix3f normal, boolean first, boolean gui) {
        long now = System.nanoTime();
        // «вверх» в пространстве модели: обратная поворотная часть позы × верх экрана/мира
        up.set(0, gui ? -1 : 1, 0);
        invNormal.set(normal).invert().transform(up);
        if (up.lengthSquared() < 1e-8f) up.set(0, 1, 0);
        up.normalize();
        if (now - simulatedAt < 1_000_000L) return; // уже посчитано в этом кадре (1-е лицо + превью)
        simulatedAt = now;
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        time += dt * speed.getF();
        flash = Math.max(0f, flash - dt * 2.8f);
        boolean on = !when.getId().equals("hit") || System.currentTimeMillis() - hitAt < 1500;
        activeK += ((on ? 1f : 0f) - activeK) * Math.min(1f, dt * 5f);
        float sp = speed.getF();
        float q = switch (quality.getId()) { case "low" -> 0.5f; case "high" -> 1.4f; default -> 1f; };
        if (activeK > 0.02f && outline.length > 0) {
            spawnAcc += dt * 90f * intensity.getF() * activeK * q * (1f + flash);
            sparkAcc += dt * sparks.getF() * 0.6f * activeK * (1f + 2f * flash);
        }
        while (spawnAcc >= 1f) { spawnAcc -= 1f; spawn(false); }
        while (sparkAcc >= 1f) { sparkAcc -= 1f; spawn(true); }
        for (int i = 0; i < count; i++) {
            age[i] += dt * sp;
            if (age[i] >= life[i]) {
                kill(i);
                i--;
                continue;
            }
            // плавное «течение»: вверх, немного наружу, мягкие завихрения от гладкого шума
            float f = age[i] / life[i];
            float swirl = (float) Math.sin(time * 2.1f + seed[i] * 31f + f * 4f) * 0.06f;
            float rise = spark[i] ? 0.55f : 0.20f + 0.25f * f;
            px[i] += (vx[i] + up.x * rise + swirl * up.y) * dt * sp;
            py[i] += (vy[i] + up.y * rise - swirl * up.x) * dt * sp;
            pz[i] += (vz[i] + up.z * rise) * dt * sp;
            float drag = spark[i] ? 0.985f : 0.94f;
            vx[i] *= drag; vy[i] *= drag; vz[i] *= drag;
        }
    }

    private void spawn(boolean isSpark) {
        if (count >= MAX || outline.length == 0) return;
        int i = count++;
        int p = rnd.nextInt(outline.length / 4) * 4;
        spark[i] = isSpark;
        seed[i] = rnd.nextFloat();
        float s = size.getF();
        float out = (0.01f + rnd.nextFloat() * 0.03f) * s;
        px[i] = outline[p] + outline[p + 2] * out;
        py[i] = outline[p + 1] + outline[p + 3] * out;
        pz[i] = (rnd.nextFloat() - 0.5f) * 0.04f;
        float push = isSpark ? 0.25f : 0.06f;
        vx[i] = outline[p + 2] * push * s + (rnd.nextFloat() - 0.5f) * 0.04f;
        vy[i] = outline[p + 3] * push * s + (rnd.nextFloat() - 0.5f) * 0.04f;
        vz[i] = (rnd.nextFloat() - 0.5f) * 0.04f;
        life[i] = isSpark ? 0.7f + rnd.nextFloat() * 0.7f : 0.45f + rnd.nextFloat() * 0.45f;
        sz[i] = isSpark ? 0.009f * s : (0.022f + rnd.nextFloat() * 0.022f) * s;
        age[i] = 0;
    }

    private void kill(int i) {
        int j = --count;
        px[i] = px[j]; py[i] = py[j]; pz[i] = pz[j];
        vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        age[i] = age[j]; life[i] = life[j]; sz[i] = sz[j]; seed[i] = seed[j]; spark[i] = spark[j];
    }

    // ── отрисовка (аддитивное свечение) ──────────────────────

    private void draw(VertexConsumer vc, Matrix4f m, boolean camAxes, boolean gui) {
        curPose = m;
        inv.set(m).invert();
        right.set(1, 0, 0);
        upv.set(0, 1, 0);
        if (camAxes) {
            Quaternionf qr = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            qr.transform(right);
            qr.transform(upv);
        }
        // оси экрана — сразу в пространство модели (один раз на кадр): вершины спрайтов строятся там, одно умножение на вершину
        inv.transformDirection(right);
        inv.transformDirection(upv);
        // какой стороной веер смотрит на камеру: у руки от 1-го лица поза бывает зеркальной — тогда меняем обход
        if (gui) inv.transformDirection(toCam.set(0, 0, 1));
        else inv.transformPosition(toCam.set(0, 0, 0)).sub(bx0 + bS * 0.5f, by0 + bS * 0.5f, bz);
        flip = (right.y * upv.z - right.z * upv.y) * toCam.x + (right.z * upv.x - right.x * upv.z) * toCam.y + (right.x * upv.y - right.y * upv.x) * toCam.z < 0;
        float breathe = 0.88f + 0.12f * (float) Math.sin(time * 1.7f);
        float k = intensity.getF();
        // 1) сияние по контуру: мягкие пятна на точках силуэта — единая светящаяся «оболочка»
        int step = switch (quality.getId()) { case "low" -> 3; case "high" -> 1; default -> 2; };
        float hr = 0.038f * size.getF() * bS;
        for (int p = 0; p < outline.length; p += 4 * step) {
            float sd = (p * 0.618f) % 1f;
            float w = 0.75f + 0.25f * (float) Math.sin(time * 2.3f + sd * 12f);
            int c = colorAt(0.12f, sd, 0.22f * k * breathe * w * (1f + flash));
            sprite(vc, model(outline[p] + outline[p + 2] * 0.015f, outline[p + 1] + outline[p + 3] * 0.015f, 0), hr * (1f + 0.4f * flash), 1f, c);
        }
        // 2) языки и искры
        for (int i = 0; i < count; i++) {
            float f = age[i] / life[i];
            float fadeIn = Math.min(1f, f / 0.12f);
            float fadeOut = 1f - f;
            if (spark[i]) {
                float tw = 0.6f + 0.4f * (float) Math.sin(time * 18f + seed[i] * 40f);
                int c = colorAt(0.05f, seed[i], 0.95f * fadeIn * fadeOut * tw);
                sprite(vc, model(px[i], py[i], pz[i]), sz[i] * bS, 1f, c);
            } else {
                // ease-out: язык растёт, потом сжимается и тает
                float r = sz[i] * bS * (0.6f + 0.6f * (float) Math.sin(Math.PI * Math.min(1f, f * 1.2f)));
                int c = colorAt(f, seed[i], 0.55f * fadeIn * fadeOut * fadeOut * k);
                sprite(vc, model(px[i], py[i], pz[i]), r, 2.2f, c);
                // мягкий ореол-«bloom» вокруг языка
                if (!quality.getId().equals("low")) sprite(vc, model(px[i], py[i], pz[i]), r * 2.2f, 1.2f, Colors.mulAlpha(c, 0.25f));
            }
        }
    }

    /** Точка в долях модели → координаты модели (в tmp). */
    private Vector3f model(float fx, float fy, float fz) {
        return tmp.set(bx0 + fx * bS, by0 + fy * bS, bz + fz * bS);
    }

    private final float[] cx = new float[3];

    /**
     * Мягкий спрайт к камере: центр яркий, край прозрачный; stretch > 1 — вытянут вверх (язык пламени).
     * Строится в пространстве вида (оси экрана) и возвращается обратной матрицей позы.
     */
    private final Vector3f toCam = new Vector3f();
    private boolean flip;
    private static final int SEG = 6;
    private static final float[] SC = new float[SEG + 1], SS = new float[SEG + 1];
    static {
        for (int i = 0; i <= SEG; i++) {
            SC[i] = (float) Math.cos(Math.PI * 2 * i / SEG);
            SS[i] = (float) Math.sin(Math.PI * 2 * i / SEG);
        }
    }

    private void sprite(VertexConsumer vc, Vector3f center, float r, float stretch, int c) {
        if ((c >>> 24) == 0 || r <= 0) return;
        float ox = center.x, oy = center.y, oz = center.z;
        int e = c & 0xFFFFFF;
        // веер треугольников в плоскости экрана; обход против часовой стрелки — лицом к камере (одной стороной)
        for (int i = 0; i < SEG; i++) {
            float c0 = SC[i] * r, s0 = SS[i] * r, c1 = SC[i + 1] * r, s1 = SS[i + 1] * r;
            // верхняя половина вытягивается — форма капли
            float u0 = s0 > 0 ? s0 * stretch : s0, u1 = s1 > 0 ? s1 * stretch : s1;
            dev.mraz1visual.render.FxVertex.putGlow(vc, curPose, ox, oy, oz, c);
            if (flip) { float t0 = c0; c0 = c1; c1 = t0; t0 = u0; u0 = u1; u1 = t0; }
            dev.mraz1visual.render.FxVertex.putGlow(vc, curPose, ox + right.x * c0 + upv.x * u0, oy + right.y * c0 + upv.y * u0, oz + right.z * c0 + upv.z * u0, e);
            dev.mraz1visual.render.FxVertex.putGlow(vc, curPose, ox + right.x * c1 + upv.x * u1, oy + right.y * c1 + upv.y * u1, oz + right.z * c1 + upv.z * u1, e);
            dev.mraz1visual.render.FxVertex.putGlow(vc, curPose, ox, oy, oz, c);
        }
    }
}
