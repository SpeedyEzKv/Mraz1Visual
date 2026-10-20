package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.ItemFx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Объёмные мечи вместо плоской модели — только картинка у тебя, сам предмет тот же.
 * Шесть характеров: энергетическая катана (по лезвию бежит световая волна), кристальный клинок (грани и внутренний свет),
 * обсидиан (матовый чёрный с пульсирующими прожилками), ледяной (морозная дымка и искры), «жидкое стекло»
 * (прозрачный, с френелевой кромкой) и церемониальный золотой (гравировка, тёплый свет).
 *
 * Рисуется в пространстве модели предмета (см. {@link ItemFx}): поза уже та, что у ванильного меча —
 * в руке от 1-го и 3-го лица, в превью. Освещение — по нормалям граней в пространстве вида (рассеянный свет,
 * блик и кромка-«френель»), свечение — аддитивными оболочками. След при взмахе — сплайн по точкам острия.
 */
public final class CustomSwords extends Module {
    private static CustomSwords instance;
    private static final long START = System.nanoTime();
    private static final String[] STYLES = {"katana", "Энергетическая катана", "crystal", "Кристальный клинок", "obsidian", "Обсидиан",
            "ice", "Ледяной", "glass", "Жидкое стекло", "gold", "Церемониальный золотой"};

    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0, STYLES)
            .alias("energy", "glass").alias("shadow", "obsidian").alias("fire", "gold"));
    final BoolSetting perType = add(new BoolSetting("per_type", "Свой стиль для каждого меча", false).group("Типы"));
    final EnumSetting styleWood = add(new EnumSetting("style_wood", "Деревянный и каменный", 4, STYLES).group("Типы"));
    final EnumSetting styleIron = add(new EnumSetting("style_iron", "Железный и золотой", 3, STYLES).group("Типы"));
    final EnumSetting styleDiamond = add(new EnumSetting("style_diamond", "Алмазный", 1, STYLES).group("Типы"));
    final EnumSetting styleNetherite = add(new EnumSetting("style_netherite", "Незеритовый", 2, STYLES).group("Типы"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Свечение", 0,
            "preset", "Как у стиля", "ether", "Эфир", "amethyst", "Аметист", "sunset", "Закат", "emerald", "Изумруд", "obsidian", "Обсидиан",
            "chrome", "Хром", "custom", "Свой градиент").alias("solid", "custom").alias("gradient", "custom").alias("rainbow", "chrome"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFF7FD8FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFFB8B4F2));
    final SliderSetting glow = add(new SliderSetting("glow", "Свечение", 70, 0, 100, 5, " %"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость анимации", 1, 0.2, 3, 0.05, "×"));
    final SliderSetting length = add(new SliderSetting("length", "Длина клинка", 1, 0.8, 1.3, 0.02, "×"));
    final BoolSetting trail = add(new BoolSetting("trail", "След при взмахе", true).group("След"));
    final SliderSetting trailTime = add(new SliderSetting("trail_time", "Длина следа", 0.22, 0.08, 0.5, 0.01, " с").group("След"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность следа", 70, 10, 100, 5, " %").group("След"));
    final EnumSetting quality = add(new EnumSetting("quality", "Качество", 1, "low", "Низкое", "medium", "Среднее", "high", "Высокое"));

    // параметры текущего клинка (считаются в begin) — без аллокаций в кадре
    private final float[] box = new float[6];
    private float ox, oy, oz, L, S;          // начало оси (торец рукояти), длина клинка-оси, размер модели
    private String st = "katana";
    private float time;
    private int segs;

    // освещение: нормаль в пространстве вида
    private final Vector3f nrm = new Vector3f(), tmp = new Vector3f(), tmp2 = new Vector3f();
    private Matrix3f normalMat = new Matrix3f();
    private static final Vector3f LIGHT = new Vector3f(0.35f, 0.75f, 0.55f).normalize();
    private static final Vector3f HALF = new Vector3f(LIGHT).add(0, 0, 1).normalize();

    // след: основание и остриё в пространстве вида за последние кадры
    private static final int TRAIL = 48;
    private final float[] tbx = new float[TRAIL], tby = new float[TRAIL], tbz = new float[TRAIL];
    private final float[] ttx = new float[TRAIL], tty = new float[TRAIL], ttz = new float[TRAIL];
    private final long[] tt = new long[TRAIL];
    private int trailHead;
    private final Matrix4f inv = new Matrix4f();
    private Matrix4f curPose = new Matrix4f();

    public CustomSwords() {
        super("custom_swords", "3D Swords", "Объёмные мечи: катана, кристалл, обсидиан, лёд, стекло, золото — со свечением и следом.", Category.SWORDS);
        instance = this;
    }

    // ── вход из ItemFx ───────────────────────────────────────

    /** Свой меч вместо квадов модели. Поза — в пространстве модели предмета. */
    public static boolean submitModel(PoseStack ps, SubmitNodeCollector c, ItemDisplayContext ctx, List<BakedQuad> quads, Item item, boolean gui) {
        CustomSwords s = instance;
        if (s == null || !s.isEnabled()) return false;
        // иконки в инвентаре и предметы на земле — ванильные: там своя отрисовка и кэш картинок
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED) return false;
        if (!ItemFx.bounds(quads, s.box)) return false;
        String style = s.styleFor(item);
        float time = (System.nanoTime() - START) / 1e9f * s.speed.getF();
        boolean first = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        float swing = first && Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getAttackAnim(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true)) : 0f;
        float b0 = s.box[0], b1 = s.box[1], b2 = s.box[2], b3 = s.box[3], b4 = s.box[4], b5 = s.box[5];
        c.submitCustomGeometry(ps, dev.mraz1visual.render.FxVertex.solid(), (pose, vc) -> s.paint(vc, pose, style, time, b0, b1, b2, b3, b4, b5, false, 0f, first, gui));
        c.submitCustomGeometry(ps, dev.mraz1visual.render.FxVertex.glow(), (pose, vc) -> s.paint(vc, pose, style, time, b0, b1, b2, b3, b4, b5, true, swing, first, gui));
        return true;
    }

    private String styleFor(Item item) {
        if (!perType.get() || item == null) return style.getId();
        if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD) return styleWood.getId();
        if (item == Items.DIAMOND_SWORD) return styleDiamond.getId();
        if (item == Items.NETHERITE_SWORD) return styleNetherite.getId();
        return styleIron.getId();
    }

    // ── кадр ─────────────────────────────────────────────────

    private void paint(VertexConsumer vc, PoseStack.Pose pose, String style, float time, float x0, float y0, float z0, float x1, float y1, float z1,
                       boolean glowPass, float swing, boolean first, boolean gui) {
        Matrix4f m = pose.pose();
        normalMat = pose.normal();
        st = style;
        additive = glowPass;
        this.time = time;
        S = Math.max(x1 - x0, y1 - y0);
        // ось клинка — диагональ спрайта: от торца рукояти (снизу слева) к острию (сверху справа)
        ox = x0 + S * 0.05f;
        oy = y0 + S * 0.05f;
        oz = (z0 + z1) / 2f;
        L = S * 1.30f * length.getF();
        segs = switch (quality.getId()) { case "low" -> 6; case "high" -> 18; default -> 12; };
        viewOrtho = first || gui;
        if (!glowPass) solid(vc, m);
        else {
            glowShell(vc, m);
            if (trail.get() && first) trailPass(vc, m, swing);
        }
    }

    // ── форма: ось A = (1,1,0)/√2, поперёк B = (−1,1,0)/√2, толщина N = (0,0,1); a — расстояние по оси в долях L ──

    private static final float AX = 0.70710678f, AY = 0.70710678f, BX = -0.70710678f, BY = 0.70710678f;
    private static final float GRIP0 = 0.04f, GRIP1 = 0.24f, GUARD = 0.255f, BLADE0 = 0.27f;

    /** Ширина клинка (в долях S) по доле длины клинка t. */
    private float width(float t) {
        float tip = t > 0.86f ? (1f - t) / 0.14f : 1f;
        tip = (float) Math.sqrt(Math.max(0f, tip));
        float w = switch (st) {
            case "katana" -> 0.040f * (1f - t * 0.15f);
            case "crystal" -> 0.070f * (1f + 0.12f * (float) Math.sin(t * 11f));
            case "obsidian" -> 0.064f * (1f - t * 0.2f);
            case "ice" -> 0.058f * (1f + 0.22f * Math.abs((float) Math.sin(t * 26f)) * (1f - t));
            case "glass" -> 0.052f;
            default -> 0.056f * (1f - t * 0.25f) * (1f + 0.15f * (float) Math.exp(-t * 18f)); // золото: расширение у гарды
        };
        return Math.max(0.0008f, w * tip) * S;
    }

    /** Изгиб (смещение поперёк клинка) — только у катаны. */
    private float curve(float t) {
        return st.equals("katana") ? 0.07f * S * t * t : 0f;
    }

    private float thick() {
        return switch (st) { case "crystal" -> 0.030f; case "obsidian" -> 0.022f; case "glass" -> 0.020f; default -> 0.016f; } * S;
    }

    // ── материалы ────────────────────────────────────────────

    /** Цвет металла/кристалла клинка: t — доля длины, edge — 0 середина, 1 кромка. */
    private int bladeColor(float t, float edge) {
        return switch (st) {
            case "katana" -> Colors.lerp(0xFFB9C2CF, 0xFFF4F7FB, edge * 0.8f + t * 0.1f);
            case "crystal" -> Colors.withAlpha(Colors.lerp(0xFF86C8FF, 0xFFE2F2FF, edge), 0xC8);
            case "obsidian" -> Colors.lerp(0xFF0E0C14, 0xFF2A2436, edge * 0.7f);
            case "ice" -> Colors.withAlpha(Colors.lerp(0xFFAEDCF5, 0xFFF4FCFF, edge * 0.9f), 0xD8);
            case "glass" -> Colors.withAlpha(Colors.lerp(0xFFE9F2FF, 0xFFFFFFFF, edge), 0x48 + Math.round(0x60 * edge));
            default -> Colors.lerp(0xFFB8862A, 0xFFFFE7A3, edge * 0.85f + 0.1f * (float) Math.sin(t * 8f)); // золото
        };
    }

    private float specular() {
        return switch (st) { case "obsidian" -> 0.25f; case "glass", "crystal" -> 1.1f; case "gold" -> 0.9f; default -> 0.7f; };
    }

    private float rim() {
        return switch (st) { case "glass" -> 1.0f; case "crystal", "ice" -> 0.6f; case "obsidian" -> 0.15f; default -> 0.25f; };
    }

    /** Цвет свечения: пресет стиля или выбранный в палитре. */
    private int glowColor(float t) {
        String mode = colorMode.getId();
        if (!mode.equals("preset")) return Palette.color(mode, t, time * 0.5f, color.get(), color2.get());
        return switch (st) {
            case "katana" -> Palette.color("ether", t, 0, 0, 0);
            case "crystal" -> Palette.color("ether", t * 0.6f, 0, 0, 0);
            case "obsidian" -> Palette.color("obsidian", 0.25f + t * 0.4f, 0, 0, 0);
            case "ice" -> Colors.lerp(0xFFF2FBFF, 0xFF8FD3FF, t);
            case "glass" -> Palette.color("chrome", t, time * 0.4f, 0, 0);
            default -> Palette.color("sunset", t * 0.6f, 0, 0, 0);
        };
    }

    private int gripColor(int i) {
        return switch (st) {
            case "katana" -> (i & 1) == 0 ? 0xFF1A1C24 : 0xFF3A3F52;      // обмотка ромбами
            case "crystal" -> 0xFF4A3F8A;
            case "obsidian" -> 0xFF16121E;
            case "ice" -> 0xFF5D8BAA;
            case "glass" -> 0xFF2C2F3A;
            default -> (i & 1) == 0 ? 0xFF3A1E14 : 0xFF5A2E1A;           // кожа
        };
    }

    private int guardColor() {
        return switch (st) {
            case "katana" -> 0xFF8C7A4E;
            case "crystal" -> 0xFFB8B4F2;
            case "obsidian" -> 0xFF231C30;
            case "ice" -> 0xFFDDF4FF;
            case "glass" -> 0xCCE6EEFF;
            default -> 0xFFE0B24A;
        };
    }

    // ── твёрдая часть ────────────────────────────────────────

    private void solid(VertexConsumer vc, Matrix4f m) {
        float th = thick();
        // навершие и рукоять сегментами (у катаны и золотого — «обмотка»)
        prism(vc, m, 0f, GRIP0, 0.05f * S, 0.045f * S, th * 1.8f, guardColor(), st.equals("glass") ? 0.6f : 0.9f);
        int gripSegs = 5;
        for (int i = 0; i < gripSegs; i++) {
            float a0 = GRIP0 + (GRIP1 - GRIP0) * i / gripSegs, a1 = GRIP0 + (GRIP1 - GRIP0) * (i + 1) / gripSegs;
            prism(vc, m, a0, a1, 0.032f * S, 0.032f * S, th * 1.4f, gripColor(i), 0.35f);
        }
        guard(vc, m, th);
        // клинок: ромб в сечении, грани освещены
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            bladeSegment(vc, m, t0, t1, 1f, th, false);
        }
        // золото: гравировка — тёмная линия по долу
        if (st.equals("gold")) {
            for (int i = 1; i < segs - 2; i++) {
                float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
                strip(vc, m, t0, t1, 0.006f * S, th * 1.02f, 0xFF7A5418, 0xFF7A5418);
            }
        }
    }

    /** Гарда: у катаны — круглая цуба, у золотого — широкая изогнутая, у остальных — брусок. */
    private void guard(VertexConsumer vc, Matrix4f m, float th) {
        int c = guardColor();
        float a = GUARD * L;
        if (st.equals("katana")) {
            int n = 14;
            float r = 0.075f * S, d = 0.012f * S;
            for (int i = 0; i < n; i++) {
                double p0 = Math.PI * 2 * i / n, p1 = Math.PI * 2 * (i + 1) / n;
                float b0 = (float) Math.cos(p0) * r, n0 = (float) Math.sin(p0) * r * 0.55f;
                float b1 = (float) Math.cos(p1) * r, n1 = (float) Math.sin(p1) * r * 0.55f;
                // обод и две плоскости диска
                litQuad(vc, m, a - d, b0, n0, a + d, b0, n0, a + d, b1, n1, a - d, b1, n1, c, c, 0.9f);
                litTri(vc, m, a + d, 0, 0, a + d, b0, n0, a + d, b1, n1, Colors.shade(c, 0.85f), 0.9f);
                litTri(vc, m, a - d, 0, 0, a - d, b1, n1, a - d, b0, n0, Colors.shade(c, 0.85f), 0.9f);
            }
            return;
        }
        float w = switch (st) { case "gold" -> 0.17f; case "ice" -> 0.15f; case "obsidian" -> 0.13f; case "glass" -> 0.10f; default -> 0.12f; } * S;
        float d = 0.018f * S, t2 = th * 2.2f;
        float lift = st.equals("gold") ? 0.03f * S : 0f; // золото: концы гарды загнуты к клинку
        // брусок из трёх частей: середина и два загнутых плеча
        box(vc, m, a - d, a + d, -w * 0.35f, w * 0.35f, t2, c);
        box(vc, m, a - d + lift * 0.3f, a + d + lift, w * 0.35f, w, t2 * 0.8f, c);
        box(vc, m, a - d + lift * 0.3f, a + d + lift, -w, -w * 0.35f, t2 * 0.8f, c);
    }

    /** Отрезок клинка между долями t0..t1: ромб (+B,+N,−B,−N), widthMul — для оболочек свечения. */
    private void bladeSegment(VertexConsumer vc, Matrix4f m, float t0, float t1, float widthMul, float th, boolean glowShell) {
        float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
        float w0 = width(t0) * widthMul, w1 = width(t1) * widthMul;
        float c0 = curve(t0), c1 = curve(t1);
        float th0 = th * widthMul * (w0 < 0.002f * S ? 0.3f : 1f), th1 = th * widthMul * (w1 < 0.002f * S ? 0.3f : 1f);
        if (glowShell) {
            // оболочка свечения: края прозрачные, середина — цвет
            int g0 = glowColor(t0), g1 = glowColor(t1);
            float k = glow.getF() / 100f * pulse(t0);
            int a0c = Colors.withAlpha(g0, Math.round(70 * k)), a1c = Colors.withAlpha(g1, Math.round(70 * k));
            int e0 = g0 & 0xFFFFFF, e1 = g1 & 0xFFFFFF;
            rawQuad(vc, m, a0, c0 + w0, 0, a1, c1 + w1, 0, a1, c1, th1, a0, c0, th0, e0, e1, a1c, a0c);
            rawQuad(vc, m, a0, c0, th0, a1, c1, th1, a1, c1 - w1, 0, a0, c0 - w0, 0, a0c, a1c, e1, e0);
            rawQuad(vc, m, a0, c0 - w0, 0, a1, c1 - w1, 0, a1, c1, -th1, a0, c0, -th0, e0, e1, a1c, a0c);
            rawQuad(vc, m, a0, c0, -th0, a1, c1, -th1, a1, c1 + w1, 0, a0, c0 + w0, 0, a0c, a1c, e1, e0);
            return;
        }
        int mid0 = bladeColor(t0, 0f), mid1 = bladeColor(t1, 0f), edge0 = bladeColor(t0, 1f), edge1 = bladeColor(t1, 1f);
        litQuad(vc, m, a0, c0 + w0, 0, a1, c1 + w1, 0, a1, c1, th1, a0, c0, th0, edge0, mid1, 1f);
        litQuad(vc, m, a0, c0, th0, a1, c1, th1, a1, c1 - w1, 0, a0, c0 - w0, 0, mid0, edge1, 1f);
        litQuad(vc, m, a0, c0 - w0, 0, a1, c1 - w1, 0, a1, c1, -th1, a0, c0, -th0, edge0, mid1, 1f);
        litQuad(vc, m, a0, c0, -th0, a1, c1, -th1, a1, c1 + w1, 0, a0, c0 + w0, 0, mid0, edge1, 1f);
    }

    /** Плоская полоска по оси клинка (гравировка, прожилки) поверх грани. */
    private void strip(VertexConsumer vc, Matrix4f m, float t0, float t1, float hw, float nOff, int c0, int c1) {
        float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
        float cc0 = curve(t0), cc1 = curve(t1);
        for (int side = -1; side <= 1; side += 2) {
            float n = nOff * side;
            rawQuad(vc, m, a0, cc0 - hw, n, a1, cc1 - hw, n, a1, cc1 + hw, n, a0, cc0 + hw, n, c0, c1, c1, c0);
        }
    }

    private float pulse(float t) {
        return switch (st) {
            case "obsidian" -> 0.6f + 0.4f * (float) Math.sin(time * 1.6f - t * 3f);
            case "gold" -> 0.85f + 0.15f * (float) Math.sin(time * 1.2f);
            default -> 0.8f + 0.2f * (float) Math.sin(time * 2.4f);
        };
    }

    // ── свечение ─────────────────────────────────────────────

    private void glowShell(VertexConsumer vc, Matrix4f m) {
        float g = glow.getF() / 100f;
        if (g < 0.01f) return;
        // ореол клинка: мягкие ленты в плоскости экрана вдоль оси — ближняя ярче, дальняя мягче (как bloom)
        glowRibbon(vc, m, 2.4f, 0.42f * g);
        if (!quality.getId().equals("low")) glowRibbon(vc, m, 5.0f, 0.16f * g);
        edgeGlint(vc, m, g);
        switch (st) {
            case "katana" -> lightWave(vc, m, g);
            case "obsidian" -> veins(vc, m, g);
            case "crystal" -> core(vc, m, g, 0.9f);
            case "ice" -> frost(vc, m, g);
            case "glass" -> core(vc, m, g, 0.45f);
            default -> gem(vc, m, g);
        }
    }

    private final float[] rx = new float[40], ry = new float[40], rz = new float[40], rw = new float[40];
    private final int[] rc = new int[40];
    private boolean viewOrtho;

    /**
     * Лента свечения вдоль оси клинка, развёрнутая к камере: в пространстве вида строится полоса,
     * в середине — цвет, к краям прозрачно; вершины возвращаются в пространство модели обратной матрицей.
     */
    private void glowRibbon(VertexConsumer vc, Matrix4f m, float widthMul, float alpha) {
        int n = Math.min(39, segs + 2);
        inv.set(m).invert();
        curPose = m;
        // масштаб модели → вида (для ширины)
        m.transformDirection(tmp2.set(BX, BY, 0));
        float scale = tmp2.length();
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float a = (BLADE0 - 0.02f + t * (1.02f - BLADE0 + 0.02f)) * L;
            m.transformPosition(tmp.set(ox + AX * a + BX * curve(Math.min(1f, t)), oy + AY * a + BY * curve(Math.min(1f, t)), oz), tmp2);
            rx[i] = tmp2.x; ry[i] = tmp2.y; rz[i] = tmp2.z;
            float tt = Math.min(1f, t);
            float fade = (float) Math.sin(Math.PI * Math.min(1f, t * 1.05f)); // к гарде и острию мягче
            rw[i] = (width(Math.min(0.99f, tt)) + 0.012f * S) * widthMul * scale * (0.55f + 0.45f * fade);
            rc[i] = Colors.withAlpha(glowColor(tt), Math.round(255 * alpha * (0.35f + 0.65f * fade) * pulse(tt)));
        }
        for (int i = 0; i < n; i++) {
            int j = i + 1;
            float dx = rx[j] - rx[i], dy = ry[j] - ry[i], dz = rz[j] - rz[i];
            // к камере: в пространстве вида — ось Z (1-е лицо/превью); в мире камера в начале координат
            float vx0 = 0, vy0 = 0, vz0 = 1;
            if (!viewOrtho) { vx0 = -rx[i]; vy0 = -ry[i]; vz0 = -rz[i]; }
            float sx = dy * vz0 - dz * vy0, sy = dz * vx0 - dx * vz0, sz = dx * vy0 - dy * vx0;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-7f) continue;
            sx /= sl; sy /= sl; sz /= sl;
            float wi = rw[i], wj = rw[j];
            int ci = rc[i], cj = rc[j], ei = ci & 0xFFFFFF, ej = cj & 0xFFFFFF;
            viewQuad(vc, rx[i] - sx * wi, ry[i] - sy * wi, rz[i] - sz * wi, ei, rx[j] - sx * wj, ry[j] - sy * wj, rz[j] - sz * wj, ej,
                    rx[j], ry[j], rz[j], cj, rx[i], ry[i], rz[i], ci);
            viewQuad(vc, rx[i], ry[i], rz[i], ci, rx[j], ry[j], rz[j], cj,
                    rx[j] + sx * wj, ry[j] + sy * wj, rz[j] + sz * wj, ej, rx[i] + sx * wi, ry[i] + sy * wi, rz[i] + sz * wi, ei);
        }
    }

    /** Тонкий блик по режущей кромке и хребту: у обсидиана тусклый, у стекла и стали — яркий. */
    private void edgeGlint(VertexConsumer vc, Matrix4f m, float g) {
        float k = switch (st) { case "obsidian" -> 0.25f; case "gold" -> 0.7f; case "glass", "crystal" -> 1f; default -> 0.8f; };
        float th = thick();
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
            float w0 = width(t0), w1 = width(t1), c0 = curve(t0), c1 = curve(t1);
            // блик ярче к острию и слабо «дышит»
            float b0 = (0.35f + 0.65f * t0) * k * (0.85f + 0.15f * pulse(t0)), b1 = (0.35f + 0.65f * t1) * k * (0.85f + 0.15f * pulse(t1));
            int e0 = Colors.withAlpha(0xFFFFFFFF, Math.round(150 * b0 * Math.max(0.4f, g))), e1 = Colors.withAlpha(0xFFFFFFFF, Math.round(150 * b1 * Math.max(0.4f, g)));
            // кромка: полоса от края внутрь на 20% ширины, по верхней грани
            float lift = 0.004f * S;
            rawQuad(vc, m, a0, c0 + w0, lift, a1, c1 + w1, lift, a1, c1 + w1 * 0.75f, th * 0.25f + lift, a0, c0 + w0 * 0.75f, th * 0.25f + lift, e0, e1, e1 & 0xFFFFFF, e0 & 0xFFFFFF);
            // хребет: тонкая линия по середине клинка
            int r0 = Colors.withAlpha(0xFFFFFFFF, Math.round(70 * b0)), r1 = Colors.withAlpha(0xFFFFFFFF, Math.round(70 * b1));
            rawQuad(vc, m, a0, c0 - w0 * 0.08f, (th + 0.004f * S), a1, c1 - w1 * 0.08f, (th + 0.004f * S), a1, c1 + w1 * 0.08f, (th + 0.004f * S), a0, c0 + w0 * 0.08f, (th + 0.004f * S), r0, r1, r1, r0);
        }
    }

    /** Катана: яркая полоса бежит по кромке от гарды к острию. */
    private void lightWave(VertexConsumer vc, Matrix4f m, float g) {
        float pos = (time * 0.55f) % 1.35f - 0.15f;
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            float k0 = (float) Math.exp(-Math.pow((t0 - pos) / 0.09f, 2)), k1 = (float) Math.exp(-Math.pow((t1 - pos) / 0.09f, 2));
            if (k0 + k1 < 0.02f) continue;
            float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
            float w0 = width(t0), w1 = width(t1), c0 = curve(t0), c1 = curve(t1);
            int col0 = Colors.withAlpha(0xFFFFFFFF, Math.round(200 * k0 * g)), col1 = Colors.withAlpha(0xFFFFFFFF, Math.round(200 * k1 * g));
            int out0 = Colors.withAlpha(glowColor(0.3f), Math.round(120 * k0 * g)), out1 = Colors.withAlpha(glowColor(0.3f), Math.round(120 * k1 * g));
            rawQuad(vc, m, a0, c0 + w0 * 0.6f, 0, a1, c1 + w1 * 0.6f, 0, a1, c1 + w1 * 1.25f, 0, a0, c0 + w0 * 1.25f, 0, col0, col1, out1 & 0xFFFFFF, out0 & 0xFFFFFF);
            rawQuad(vc, m, a0, c0 + w0 * 0.6f, 0, a1, c1 + w1 * 0.6f, 0, a1, c1, thick(), a0, c0, thick(), col0, col1, out1, out0);
        }
    }

    /** Обсидиан: зигзаг прожилок акцентного цвета, медленно пульсирует волной вдоль клинка. */
    private void veins(VertexConsumer vc, Matrix4f m, float g) {
        float th = thick() * 0.55f;
        int n = segs * 2;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < n; i++) {
                float t0 = i / (float) n, t1 = (i + 1) / (float) n;
                if (t1 > 0.93f) break;
                float o0 = (float) Math.sin(i * 2.7f + side) * 0.45f, o1 = (float) Math.sin((i + 1) * 2.7f + side) * 0.45f;
                float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
                float b0 = curve(t0) + width(t0) * o0, b1 = curve(t1) + width(t1) * o1;
                float k = (0.45f + 0.55f * (float) Math.max(0, Math.sin(time * 1.4f - t0 * 9f))) * g;
                int c = Colors.withAlpha(glowColor(0.2f), Math.round(230 * k)), e = Colors.withAlpha(glowColor(0.6f), 0);
                float hw = 0.0045f * S;
                float n0 = side * (thick() * (1f - Math.abs(o0)) + 0.004f * S);
                rawQuad(vc, m, a0, b0 - hw * 2.5f, n0, a1, b1 - hw * 2.5f, n0, a1, b1, n0, a0, b0, n0, e, e, c, c);
                rawQuad(vc, m, a0, b0, n0, a1, b1, n0, a1, b1 + hw * 2.5f, n0, a0, b0 + hw * 2.5f, n0, c, c, e, e);
            }
        }
    }

    /** Кристалл и стекло: светящаяся сердцевина по оси клинка и искры на гранях. */
    private void core(VertexConsumer vc, Matrix4f m, float g, float k) {
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            float a0 = (BLADE0 + t0 * (1f - BLADE0)) * L, a1 = (BLADE0 + t1 * (1f - BLADE0)) * L;
            float hw0 = width(t0) * 0.28f, hw1 = width(t1) * 0.28f;
            int c0 = Colors.withAlpha(glowColor(t0 * 0.5f), Math.round(170 * k * g * pulse(t0))), c1 = Colors.withAlpha(glowColor(t1 * 0.5f), Math.round(170 * k * g * pulse(t1)));
            rawQuad(vc, m, a0, curve(t0) - hw0, 0, a1, curve(t1) - hw1, 0, a1, curve(t1) + hw1, 0, a0, curve(t0) + hw0, 0, c0 & 0xFFFFFF, c1 & 0xFFFFFF, c1, c0);
            rawQuad(vc, m, a0, curve(t0) + hw0, 0, a1, curve(t1) + hw1, 0, a1, curve(t1) + hw1 * 2f, 0, a0, curve(t0) + hw0 * 2f, 0, c0, c1, c1 & 0xFFFFFF, c0 & 0xFFFFFF);
        }
        // искры на гранях: короткие вспышки в случайных местах клинка
        for (int i = 0; i < 4; i++) {
            float ph = (time * 0.7f + i * 0.37f) % 1f;
            float t = (float) ((Math.sin(i * 12.9898 + Math.floor(time * 0.7f + i * 0.37f) * 4.1) * 43758.5453) % 1 + 1) % 1;
            t = 0.1f + t * 0.8f;
            float bright = (float) Math.sin(ph * Math.PI);
            star(vc, m, (BLADE0 + t * (1f - BLADE0)) * L, curve(t) + width(t) * 0.5f, thick() * 0.8f, 0.03f * S * bright, Colors.withAlpha(0xFFFFFFFF, Math.round(230 * bright * g)));
        }
    }

    /** Лёд: морозная дымка у лезвия (мягкие пятна плывут вдоль) и мелкие искры-снежинки. */
    private void frost(VertexConsumer vc, Matrix4f m, float g) {
        for (int i = 0; i < 7; i++) {
            float ph = (time * 0.18f + i / 7f) % 1f;
            float t = 0.05f + ph * 0.9f;
            float side = (i & 1) == 0 ? 1f : -1f;
            float r = 0.06f * S * (0.6f + 0.4f * (float) Math.sin(ph * Math.PI));
            int c = Colors.withAlpha(0xFFE8F7FF, Math.round(55 * g * (float) Math.sin(ph * Math.PI)));
            star(vc, m, (BLADE0 + t * (1f - BLADE0)) * L, curve(t) + side * width(t) * 1.3f, 0, r, c);
        }
        for (int i = 0; i < 5; i++) {
            float ph = (time * 0.9f + i * 0.21f) % 1f;
            float t = 0.15f + ((i * 0.618f) % 1f) * 0.75f;
            float bright = (float) Math.sin(ph * Math.PI);
            star(vc, m, (BLADE0 + t * (1f - BLADE0)) * L, curve(t) + width(t) * (1.1f + ph * 0.6f), thick() * 0.6f, 0.012f * S, Colors.withAlpha(0xFFFFFFFF, Math.round(240 * bright * g)));
        }
    }

    /** Золото: тёплый камень в навершии и мягкий свет у гарды. */
    private void gem(VertexConsumer vc, Matrix4f m, float g) {
        float k = 0.8f + 0.2f * (float) Math.sin(time * 2f);
        star(vc, m, GRIP0 * L * 0.5f, 0, thick() * 2f, 0.05f * S, Colors.withAlpha(0xFFFF8A5C, Math.round(200 * k * g)));
        star(vc, m, GUARD * L, 0, thick() * 2.3f, 0.09f * S, Colors.withAlpha(0xFFFFD27A, Math.round(90 * k * g)));
    }

    // ── след при взмахе (1-е лицо, пространство вида) ─────────

    private void trailPass(VertexConsumer vc, Matrix4f m, float swing) {
        long now = System.nanoTime();
        if (swing > 0.01f) {
            // точки основания (40% клинка) и острия — в пространство вида
            trailHead = (trailHead + 1) % TRAIL;
            float aBase = (BLADE0 + 0.35f * (1f - BLADE0)) * L, aTip = L;
            ptv(aBase, curve(0.35f), 0);
            m.transformPosition(tmp, tmp2);
            tbx[trailHead] = tmp2.x; tby[trailHead] = tmp2.y; tbz[trailHead] = tmp2.z;
            ptv(aTip, curve(1f), 0);
            m.transformPosition(tmp, tmp2);
            ttx[trailHead] = tmp2.x; tty[trailHead] = tmp2.y; ttz[trailHead] = tmp2.z;
            tt[trailHead] = now;
        }
        long life = (long) (trailTime.getF() * 1e9);
        inv.set(m).invert();
        curPose = m;
        float op = opacity.getF() / 100f;
        // сплайн Катмулла — Рома между сохранёнными точками: лента гладкая и на высоком FPS
        int count = 0;
        for (int i = 0; i < TRAIL; i++) {
            int idx = (trailHead - i + TRAIL) % TRAIL;
            if (tt[idx] == 0 || now - tt[idx] > life) break;
            count++;
        }
        if (count < 3) return;
        int sub = 3;
        float prevBx = 0, prevBy = 0, prevBz = 0, prevTx = 0, prevTy = 0, prevTz = 0, prevF = 0;
        boolean has = false;
        for (int i = 0; i < count - 1; i++) {
            for (int s = 0; s < sub; s++) {
                float u = s / (float) sub;
                int i0 = idx(i - 1, count), i1 = idx(i, count), i2 = idx(i + 1, count), i3 = idx(i + 2, count);
                float bx = cr(tbx[i0], tbx[i1], tbx[i2], tbx[i3], u), by = cr(tby[i0], tby[i1], tby[i2], tby[i3], u), bz = cr(tbz[i0], tbz[i1], tbz[i2], tbz[i3], u);
                float tx = cr(ttx[i0], ttx[i1], ttx[i2], ttx[i3], u), ty = cr(tty[i0], tty[i1], tty[i2], tty[i3], u), tz = cr(ttz[i0], ttz[i1], ttz[i2], ttz[i3], u);
                float age = ((now - tt[i1]) * (1 - u) + (now - tt[i2]) * u) / (float) life;
                float f = Math.max(0f, 1f - age);
                f = f * f;
                if (has) {
                    int cTip0 = Colors.withAlpha(glowColor(0.15f), Math.round(190 * prevF * op)), cTip1 = Colors.withAlpha(glowColor(0.15f), Math.round(190 * f * op));
                    int cB0 = Colors.withAlpha(glowColor(0.8f), 0), cB1 = Colors.withAlpha(glowColor(0.8f), 0);
                    viewQuad(vc, prevBx, prevBy, prevBz, cB0, prevTx, prevTy, prevTz, cTip0, tx, ty, tz, cTip1, bx, by, bz, cB1);
                }
                prevBx = bx; prevBy = by; prevBz = bz; prevTx = tx; prevTy = ty; prevTz = tz; prevF = f;
                has = true;
            }
        }
    }

    private int idx(int i, int count) {
        int k = Math.max(0, Math.min(count - 1, i));
        return (trailHead - k + TRAIL) % TRAIL;
    }

    private static float cr(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Четырёхугольник в пространстве вида, двусторонний. */
    private void viewQuad(VertexConsumer vc, float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                          float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
        view(vc, x1, y1, z1, c1); view(vc, x2, y2, z2, c2); view(vc, x3, y3, z3, c3); view(vc, x4, y4, z4, c4);
        view(vc, x4, y4, z4, c4); view(vc, x3, y3, z3, c3); view(vc, x2, y2, z2, c2); view(vc, x1, y1, z1, c1);
    }

    private void view(VertexConsumer vc, float x, float y, float z, int col) {
        inv.transformPosition(x, y, z, tmp);
        emit(vc, curPose, tmp.x, tmp.y, tmp.z, col);
    }

    // ── примитивы (координаты в пространстве модели: a — вдоль оси, b — поперёк, n — толщина) ──

    /** Точка оси (a, b, n) → в tmp (для следа). */
    private Vector3f ptv(float a, float b, float n) {
        return tmp.set(ox + AX * a + BX * b, oy + AY * a + BY * b, oz + n);
    }

    /** Сейчас рисуется проход свечения (аддитивный тип) — цвет вершин заранее умножается на альфу. */
    private boolean additive;

    private void emit(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c) {
        if (additive) dev.mraz1visual.render.FxVertex.putGlow(vc, m, x, y, z, c);
        else dev.mraz1visual.render.FxVertex.put(vc, m, x, y, z, c);
    }

    // вершины текущей грани
    private final float[] vx = new float[4], vy = new float[4], vz = new float[4];

    private void set(int i, float a, float b, float n) {
        vx[i] = ox + AX * a + BX * b;
        vy[i] = oy + AY * a + BY * b;
        vz[i] = oz + n;
    }

    /** Освещённая грань: c1 — у вершин 1–2, c2 — у вершин 3–4. Нормаль считается по грани и переводится в пространство вида. */
    private void litQuad(VertexConsumer vc, Matrix4f m, float a1, float b1, float n1, float a2, float b2, float n2,
                         float a3, float b3, float n3, float a4, float b4, float n4, int c1, int c2, float specMul) {
        set(0, a1, b1, n1); set(1, a2, b2, n2); set(2, a3, b3, n3); set(3, a4, b4, n4);
        emitLit(vc, m, c1, c2, specMul);
    }

    private void litTri(VertexConsumer vc, Matrix4f m, float a1, float b1, float n1, float a2, float b2, float n2, float a3, float b3, float n3, int c, float specMul) {
        set(0, a1, b1, n1); set(1, a2, b2, n2); set(2, a3, b3, n3); set(3, a3, b3, n3);
        emitLit(vc, m, c, c, specMul);
    }

    private void emitLit(VertexConsumer vc, Matrix4f m, int c1, int c2, float specMul) {
        // нормаль грани (в модели) → в пространство вида; грань двусторонняя — смотрим «к камере»
        float ux = vx[1] - vx[0], uy = vy[1] - vy[0], uz = vz[1] - vz[0];
        float wx = vx[3] - vx[0], wy = vy[3] - vy[0], wz = vz[3] - vz[0];
        if (ux * ux + uy * uy + uz * uz < 1e-12f) { ux = vx[2] - vx[1]; uy = vy[2] - vy[1]; uz = vz[2] - vz[1]; }
        if (wx * wx + wy * wy + wz * wz < 1e-12f) { wx = vx[2] - vx[0]; wy = vy[2] - vy[0]; wz = vz[2] - vz[0]; }
        nrm.set(uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx);
        normalMat.transform(nrm);
        float len = nrm.length();
        if (len < 1e-9f) nrm.set(0, 0, 1); else nrm.div(len);
        if (nrm.z < 0) nrm.negate();
        float diff = Math.max(0f, nrm.dot(LIGHT));
        float spec = (float) Math.pow(Math.max(0f, nrm.dot(HALF)), 28) * specular() * specMul;
        float fres = (float) Math.pow(1f - Math.min(1f, nrm.z), 2.0) * rim();
        int rimC = st.equals("glass") || st.equals("crystal") || st.equals("ice") ? glowColor(0.25f) : 0xFFFFFFFF;
        int k1 = shade(c1, diff, spec, fres, rimC), k2 = shade(c2, diff, spec, fres, rimC);
        // двусторонне: у части типов рендера включено отсечение граней — грань видна с любой стороны
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[0], vy[0], vz[0], k1);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[1], vy[1], vz[1], k1);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[2], vy[2], vz[2], k2);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[3], vy[3], vz[3], k2);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[3], vy[3], vz[3], k2);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[2], vy[2], vz[2], k2);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[1], vy[1], vz[1], k1);
        dev.mraz1visual.render.FxVertex.put(vc, m, vx[0], vy[0], vz[0], k1);
    }

    private static int shade(int c, float diff, float spec, float fres, int rimC) {
        float s = 0.5f + 0.55f * diff; // своё освещение граней (нормаль вершины смотрит в камеру — ванильный свет ровный)
        int a = c >>> 24;
        float r = (c >> 16 & 255) * s + 255 * spec + (rimC >> 16 & 255) * fres;
        float g = (c >> 8 & 255) * s + 255 * spec + (rimC >> 8 & 255) * fres;
        float b = (c & 255) * s + 255 * spec + (rimC & 255) * fres;
        // блик и кромка делают стекло менее прозрачным в этих местах — как настоящий отблеск
        int aa = Math.min(255, Math.round(a + (255 - a) * Math.min(1f, spec + fres * 0.6f)));
        return aa << 24 | Math.min(255, Math.round(r)) << 16 | Math.min(255, Math.round(g)) << 8 | Math.min(255, Math.round(b));
    }

    /** Грань без освещения, по цвету на вершину (свечение, следы, гравировка). */
    private void rawQuad(VertexConsumer vc, Matrix4f m, float a1, float b1, float n1, float a2, float b2, float n2,
                         float a3, float b3, float n3, float a4, float b4, float n4, int c1, int c2, int c3, int c4) {
        set(0, a1, b1, n1); set(1, a2, b2, n2); set(2, a3, b3, n3); set(3, a4, b4, n4);
        emit(vc, m, vx[0], vy[0], vz[0], c1);
        emit(vc, m, vx[1], vy[1], vz[1], c2);
        emit(vc, m, vx[2], vy[2], vz[2], c3);
        emit(vc, m, vx[3], vy[3], vz[3], c4);
        emit(vc, m, vx[3], vy[3], vz[3], c4);
        emit(vc, m, vx[2], vy[2], vz[2], c3);
        emit(vc, m, vx[1], vy[1], vz[1], c2);
        emit(vc, m, vx[0], vy[0], vz[0], c1);
    }

    /** Брусок вдоль оси (рукоять, навершие): ширина от w0 к w1, толщина th. */
    private void prism(VertexConsumer vc, Matrix4f m, float a0, float a1, float w0, float w1, float th, int c, float specMul) {
        float A0 = a0 * L, A1 = a1 * L;
        int d = Colors.shade(c, 0.8f);
        litQuad(vc, m, A0, -w0, th, A1, -w1, th, A1, w1, th, A0, w0, th, c, c, specMul);
        litQuad(vc, m, A0, w0, -th, A1, w1, -th, A1, -w1, -th, A0, -w0, -th, d, d, specMul);
        litQuad(vc, m, A0, w0, th, A1, w1, th, A1, w1, -th, A0, w0, -th, c, c, specMul);
        litQuad(vc, m, A0, -w0, -th, A1, -w1, -th, A1, -w1, th, A0, -w0, th, d, d, specMul);
        litQuad(vc, m, A0, -w0, -th, A0, -w0, th, A0, w0, th, A0, w0, -th, d, d, specMul);
    }

    /** Параллелепипед в осях (a, b, n): a и b — границы, th — половина толщины. */
    private void box(VertexConsumer vc, Matrix4f m, float a0, float a1, float b0, float b1, float th, int c) {
        int d = Colors.shade(c, 0.82f);
        litQuad(vc, m, a0, b0, th, a1, b0, th, a1, b1, th, a0, b1, th, c, c, 1f);
        litQuad(vc, m, a0, b1, -th, a1, b1, -th, a1, b0, -th, a0, b0, -th, d, d, 1f);
        litQuad(vc, m, a0, b0, -th, a1, b0, -th, a1, b0, th, a0, b0, th, d, d, 1f);
        litQuad(vc, m, a0, b1, th, a1, b1, th, a1, b1, -th, a0, b1, -th, c, c, 1f);
        litQuad(vc, m, a1, b0, -th, a1, b1, -th, a1, b1, th, a1, b0, th, c, c, 1f);
        litQuad(vc, m, a0, b0, th, a0, b1, th, a0, b1, -th, a0, b0, -th, d, d, 1f);
    }

    /** Мягкая «звёздочка» свечения: два скрещенных диска (видно с любого угла), яркий центр, прозрачный край. */
    private void star(VertexConsumer vc, Matrix4f m, float a, float b, float n, float r, int c) {
        if (r <= 0 || (c >>> 24) == 0) return;
        int e = c & 0xFFFFFF;
        int k = 6;
        for (int plane = 0; plane < 2; plane++) {
            for (int i = 0; i < k; i++) {
                float p0 = (float) (Math.PI * 2 * i / k), p1 = (float) (Math.PI * 2 * (i + 1) / k);
                float c0 = (float) Math.cos(p0) * r, s0 = (float) Math.sin(p0) * r, c1 = (float) Math.cos(p1) * r, s1 = (float) Math.sin(p1) * r;
                if (plane == 0) rawQuad(vc, m, a, b, n, a + c0, b + s0, n, a + c1, b + s1, n, a, b, n, c, e, e, c);
                else rawQuad(vc, m, a, b, n, a + c0, b, n + s0, a + c1, b, n + s1, a, b, n, c, e, e, c);
            }
        }
    }
}
