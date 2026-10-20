package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.HandFx;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Свои мечи от 1-го лица: вместо модели меча рисуется собственный клинок (катана, энергетический,
 * кристальный, теневой, ледяной, огненный) со свечением и следом при взмахе.
 * Сам предмет не меняется — это только картинка у тебя.
 */
public final class CustomSwords extends Module {
    private static CustomSwords instance;
    private static final int TRAIL = 14;
    private static final long START = System.nanoTime();

    final EnumSetting style = add(new EnumSetting("style", "Меч", 0,
            "katana", "Катана", "energy", "Энергетический", "crystal", "Кристальный", "shadow", "Теневой", "ice", "Ледяной", "fire", "Огненный"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет свечения", 0, "preset", "Как у стиля", "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFF7FD8FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFFB8B4F2));
    final SliderSetting glow = add(new SliderSetting("glow", "Свечение", 70, 0, 100, 5, " %"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость анимации", 1, 0.2, 3, 0.05, "×"));
    final SliderSetting length = add(new SliderSetting("length", "Длина клинка", 1, 0.8, 1.3, 0.02, "×"));
    final BoolSetting trail = add(new BoolSetting("trail", "След при взмахе", true));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность следа", 70, 10, 100, 5, " %"));

    // след: позиции основания и острия клинка в пространстве вида за последние кадры
    private final float[][] trailBase = new float[TRAIL][3], trailTip = new float[TRAIL][3];
    private final long[] trailTime = new long[TRAIL];
    private int trailHead;

    private final Matrix4f inv = new Matrix4f();
    private Matrix4f cur = new Matrix4f();
    private final Vector3f tmp = new Vector3f();

    public CustomSwords() {
        super("custom_swords", "Custom Swords", "6 своих мечей вместо обычного — со свечением и следом.", Category.SWORDS);
        instance = this;
    }

    /** Из миксина ItemInHandRenderer.renderItem. true — свой меч нарисован, ванильный не рисуем. */
    public static boolean render(LivingEntity e, ItemStack stack, ItemDisplayContext ctx, PoseStack ps, SubmitNodeCollector c) {
        CustomSwords s = instance;
        if (s == null || !s.isEnabled() || stack.isEmpty() || !stack.is(ItemTags.SWORDS)) return false;
        if (e != Minecraft.getInstance().player) return false;
        if (ctx != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return false;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        float time = (System.nanoTime() - START) / 1e9f * s.speed.getF();
        float swing = e.getAttackAnim(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        ps.pushPose();
        HandFx.handheld(ps, left);
        c.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> s.solid(vc, pose.pose(), time));
        c.submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> s.glowPass(vc, pose.pose(), time, swing));
        ps.popPose();
        return true;
    }

    // ── палитры стилей: лезвие у основания, у острия, кромка, свечение, гарда, рукоять ──

    private int[] palette() {
        return switch (style.getId()) {
            case "energy" -> new int[]{0xFFFFFFFF, 0xFFE8FBFF, 0xFFFFFFFF, 0xFF4FD8FF, 0xFF3A3F4A, 0xFF22252C};
            case "crystal" -> new int[]{0xC07FE8FF, 0xA0D8C8FF, 0xFFFFFFFF, 0xFF9FE0FF, 0xFFB8B4F2, 0xFF5A4FA0};
            case "shadow" -> new int[]{0xFF1A1024, 0xFF3A1A5A, 0xFFB070FF, 0xFF8A2BE2, 0xFF2A1A3A, 0xFF120A18};
            case "ice" -> new int[]{0xD0BFEFFF, 0xC0E8FBFF, 0xFFFFFFFF, 0xFF8FE3FF, 0xFFDFF6FF, 0xFF6FA8C8};
            case "fire" -> new int[]{0xFFD0301A, 0xFFFFB347, 0xFFFFF0B0, 0xFFFF7A1A, 0xFF3A2010, 0xFF5A2A10};
            default -> new int[]{0xFFC8CCD4, 0xFFE8ECF2, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF2A2A2E, 0xFF8A1F2A}; // катана
        };
    }

    private int glowColor(int preset, float t) {
        if (colorMode.getId().equals("preset")) return preset;
        return ColorFx.pick(colorMode.getId(), color.get(), color2.get(), t);
    }

    // ── геометрия ────────────────────────────────────────────
    // Ось клинка A = (1,1,0)/√2, поперёк B = (−1,1,0)/√2, толщина N = (0,0,1); t — расстояние от торца рукояти.

    private static final float AX = 0.70710678f, AY = 0.70710678f, BX = -0.70710678f, BY = 0.70710678f;

    private float bladeLen() { return 0.86f * length.getF(); }

    /** Изгиб катаны: смещение поперёк клинка в долях длины. */
    private float curve(float t) {
        return style.getId().equals("katana") ? 0.05f * t * t : 0f;
    }

    private void solid(VertexConsumer vc, Matrix4f m, float time) {
        int[] p = palette();
        float x0 = HandFx.BASE_X - 0.04f, y0 = HandFx.BASE_Y - 0.04f, z = HandFx.Z;
        // рукоять и навершие
        prism(vc, m, x0, y0, z, 0f, 0.24f, 0.028f, 0.028f, 0.024f, p[5], p[5]);
        prism(vc, m, x0, y0, z, -0.03f, 0.0f, 0.04f, 0.04f, 0.035f, p[4], p[4]);
        // гарда (поперёк клинка)
        float gw = switch (style.getId()) { case "katana" -> 0.07f; case "energy" -> 0.06f; case "ice" -> 0.16f; case "shadow" -> 0.14f; default -> 0.12f; };
        float gt = 0.25f;
        guard(vc, m, x0 + AX * gt, y0 + AY * gt, z, gw, p[4]);
        // клинок
        float L = bladeLen();
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            float w0 = width(t0), w1 = width(t1);
            float a0 = 0.27f + t0 * L, a1 = 0.27f + t1 * L;
            float c0 = curve(t0), c1 = curve(t1);
            int col0 = Colors.lerp(p[0], p[1], t0), col1 = Colors.lerp(p[0], p[1], t1);
            if (style.getId().equals("fire")) {
                float fl = 0.85f + 0.15f * (float) Math.sin(time * 9 + i);
                col0 = Colors.shade(col0, fl);
                col1 = Colors.shade(col1, fl);
            }
            bladeSegment(vc, m, x0, y0, z, a0, a1, c0, c1, w0, w1, 0.016f, col0, col1, p[2]);
        }
    }

    /** Ширина клинка по длине: у основания шире, к острию сходится. Тень — зубчатая. */
    private float width(float t) {
        float base = switch (style.getId()) { case "katana" -> 0.045f; case "crystal" -> 0.075f; case "ice" -> 0.065f; case "energy" -> 0.04f; default -> 0.06f; };
        float w = base * (t > 0.82f ? (1f - t) / 0.18f : 1f - t * 0.25f);
        if (style.getId().equals("shadow")) w *= 1f + 0.25f * (float) Math.abs(Math.sin(t * 22));
        if (style.getId().equals("crystal")) w *= 1f + 0.15f * (float) Math.sin(t * 9);
        return Math.max(0.0005f, w);
    }

    private void glowPass(VertexConsumer vc, Matrix4f m, float time, float swing) {
        int[] p = palette();
        float g = glow.getF() / 100f;
        float x0 = HandFx.BASE_X - 0.04f, y0 = HandFx.BASE_Y - 0.04f, z = HandFx.Z;
        float L = bladeLen();
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 3.2f);
        if (g > 0.01f) {
            int segs = 8;
            for (int i = 0; i < segs; i++) {
                float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
                float a0 = 0.27f + t0 * L, a1 = 0.27f + t1 * L;
                int gc0 = glowColor(p[3], t0), gc1 = glowColor(p[3], t1);
                // два слоя ореола: близкий поярче, дальний мягкий
                bladeSegment(vc, m, x0, y0, z, a0, a1, curve(t0), curve(t1), width(t0) * 2.0f, width(t1) * 2.0f, 0.03f,
                        Colors.withAlpha(gc0, Math.round(110 * g * pulse)), Colors.withAlpha(gc1, Math.round(110 * g * pulse)), Colors.withAlpha(gc0, 0));
                bladeSegment(vc, m, x0, y0, z, a0, a1, curve(t0), curve(t1), width(t0) * 3.6f, width(t1) * 3.6f, 0.05f,
                        Colors.withAlpha(gc0, Math.round(45 * g * pulse)), Colors.withAlpha(gc1, Math.round(45 * g * pulse)), Colors.withAlpha(gc0, 0));
            }
        }
        if (trail.get()) trailPass(vc, m, x0, y0, z, L, swing, glowColor(p[3], 0.5f));
    }

    /** Лента следа: точки клинка в пространстве вида копятся во время взмаха и гаснут за 0.18 с. */
    private void trailPass(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float L, float swing, int col) {
        long now = System.nanoTime();
        if (swing > 0.02f) {
            trailHead = (trailHead + 1) % TRAIL;
            m.transformPosition(x0 + AX * (0.27f + L * 0.35f), y0 + AY * (0.27f + L * 0.35f), z, tmp);
            trailBase[trailHead][0] = tmp.x; trailBase[trailHead][1] = tmp.y; trailBase[trailHead][2] = tmp.z;
            m.transformPosition(x0 + AX * (0.27f + L), y0 + AY * (0.27f + L), z, tmp);
            trailTip[trailHead][0] = tmp.x; trailTip[trailHead][1] = tmp.y; trailTip[trailHead][2] = tmp.z;
            trailTime[trailHead] = now;
        }
        inv.set(m).invert();
        cur = m;
        float op = opacity.getF() / 100f;
        for (int i = 0; i < TRAIL - 1; i++) {
            int a = (trailHead - i + TRAIL) % TRAIL, b = (a - 1 + TRAIL) % TRAIL;
            float fa = 1f - (now - trailTime[a]) / 1.8e8f, fb = 1f - (now - trailTime[b]) / 1.8e8f;
            if (fa <= 0 || fb <= 0 || trailTime[a] == 0 || trailTime[b] == 0) break;
            int ca = Colors.withAlpha(col, Math.round(170 * fa * op)), cb = Colors.withAlpha(col, Math.round(170 * fb * op));
            int ea = Colors.withAlpha(col, 0), eb = Colors.withAlpha(col, 0);
            view(vc, trailBase[a], ea); view(vc, trailTip[a], ca); view(vc, trailTip[b], cb); view(vc, trailBase[b], eb);
        }
    }

    private void view(VertexConsumer vc, float[] p, int col) {
        inv.transformPosition(p[0], p[1], p[2], tmp);
        vc.addVertex(cur, tmp.x, tmp.y, tmp.z).setColor(col);
    }

    /** Отрезок клинка: ромб в сечении (поперёк B на w, по толщине N на th), с кромкой по краям. */
    private void bladeSegment(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float a0, float a1,
                              float c0, float c1, float w0, float w1, float th, int col0, int col1, int edge) {
        float cx0 = x0 + AX * a0 + BX * c0, cy0 = y0 + AY * a0 + BY * c0;
        float cx1 = x0 + AX * a1 + BX * c1, cy1 = y0 + AY * a1 + BY * c1;
        float t0 = th * (w0 / Math.max(w0, 0.0001f)), t1 = th * (w1 > 0.002f ? 1f : 0.2f);
        int e0 = Colors.lerp(col0, edge, 0.6f), e1 = Colors.lerp(col1, edge, 0.6f);
        // четыре грани ромба: (+B,+N) (+N,−B) (−B,−N) (−N,+B)
        quad(vc, m, cx0 + BX * w0, cy0 + BY * w0, z, cx1 + BX * w1, cy1 + BY * w1, z, cx1, cy1, z + t1, cx0, cy0, z + t0, e0, e1, col1, col0);
        quad(vc, m, cx0, cy0, z + t0, cx1, cy1, z + t1, cx1 - BX * w1, cy1 - BY * w1, z, cx0 - BX * w0, cy0 - BY * w0, z, col0, col1, e1, e0);
        quad(vc, m, cx0 - BX * w0, cy0 - BY * w0, z, cx1 - BX * w1, cy1 - BY * w1, z, cx1, cy1, z - t1, cx0, cy0, z - t0, e0, e1, Colors.shade(col1, 0.8f), Colors.shade(col0, 0.8f));
        quad(vc, m, cx0, cy0, z - t0, cx1, cy1, z - t1, cx1 + BX * w1, cy1 + BY * w1, z, cx0 + BX * w0, cy0 + BY * w0, z, Colors.shade(col0, 0.8f), Colors.shade(col1, 0.8f), e1, e0);
    }

    /** Прямоугольная призма вдоль оси клинка (рукоять, навершие). */
    private void prism(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float a0, float a1, float w, float w2, float th, int c, int c2) {
        float ax0 = x0 + AX * a0, ay0 = y0 + AY * a0, ax1 = x0 + AX * a1, ay1 = y0 + AY * a1;
        int s = Colors.shade(c, 0.75f);
        quad(vc, m, ax0 + BX * w, ay0 + BY * w, z + th, ax1 + BX * w2, ay1 + BY * w2, z + th, ax1 - BX * w2, ay1 - BY * w2, z + th, ax0 - BX * w, ay0 - BY * w, z + th, c, c, c, c);
        quad(vc, m, ax0 - BX * w, ay0 - BY * w, z - th, ax1 - BX * w2, ay1 - BY * w2, z - th, ax1 + BX * w2, ay1 + BY * w2, z - th, ax0 + BX * w, ay0 + BY * w, z - th, s, s, s, s);
        quad(vc, m, ax0 + BX * w, ay0 + BY * w, z - th, ax1 + BX * w2, ay1 + BY * w2, z - th, ax1 + BX * w2, ay1 + BY * w2, z + th, ax0 + BX * w, ay0 + BY * w, z + th, c2, c2, c2, c2);
        quad(vc, m, ax0 - BX * w, ay0 - BY * w, z + th, ax1 - BX * w2, ay1 - BY * w2, z + th, ax1 - BX * w2, ay1 - BY * w2, z - th, ax0 - BX * w, ay0 - BY * w, z - th, s, s, s, s);
    }

    /** Гарда: плоский брусок поперёк клинка. */
    private void guard(VertexConsumer vc, Matrix4f m, float cx, float cy, float z, float w, int c) {
        float d = 0.018f, th = 0.032f;
        int s = Colors.shade(c, 0.7f);
        float x1 = cx + BX * w - AX * d, y1 = cy + BY * w - AY * d, x2 = cx + BX * w + AX * d, y2 = cy + BY * w + AY * d;
        float x3 = cx - BX * w + AX * d, y3 = cy - BY * w + AY * d, x4 = cx - BX * w - AX * d, y4 = cy - BY * w - AY * d;
        quad(vc, m, x1, y1, z + th, x2, y2, z + th, x3, y3, z + th, x4, y4, z + th, c, c, c, c);
        quad(vc, m, x4, y4, z - th, x3, y3, z - th, x2, y2, z - th, x1, y1, z - th, s, s, s, s);
        quad(vc, m, x2, y2, z - th, x3, y3, z - th, x3, y3, z + th, x2, y2, z + th, c, c, c, c);
        quad(vc, m, x4, y4, z - th, x1, y1, z - th, x1, y1, z + th, x4, y4, z + th, s, s, s, s);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int c1, int c2, int c3, int c4) {
        vc.addVertex(m, x1, y1, z1).setColor(c1);
        vc.addVertex(m, x2, y2, z2).setColor(c2);
        vc.addVertex(m, x3, y3, z3).setColor(c3);
        vc.addVertex(m, x4, y4, z4).setColor(c4);
    }
}
