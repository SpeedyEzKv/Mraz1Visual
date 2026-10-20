package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.BodyFrame;
import dev.mraz1visual.render.ColorFx;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Объёмные крылья: ангельские из перьев, драконьи с костями и перепонкой, энергетические со свечением,
 * механические из пластин. Взмахи зависят от движения: на месте — медленно, на бегу — чаще,
 * в прыжке — широко, на элитрах — раскрыты для планирования, крадучись — сложены.
 */
public final class Wings3D extends Module {
    private static Wings3D instance;
    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0,
            "angel", "Ангельские (перья)", "dragon", "Драконьи", "energy", "Энергетические", "mech", "Механические"));
    final EnumSetting colorMode = add(new EnumSetting("color_mode", "Цвет", 0, "preset", "Как у стиля", "solid", "Один", "gradient", "Градиент", "rainbow", "Радуга"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет 1", 0xFFF2F0FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Цвет 2", 0xFFB8B4F2));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 1.8, 0.05, "×"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость взмахов", 1, 0.3, 3, 0.05, "×"));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 90, 20, 100, 5, " %"));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 90, 10, 100, 5, " %"));
    final BoolSetting glow = add(new BoolSetting("glow", "Свечение", true));

    private final BodyFrame frame = new BodyFrame();
    private float phase, amp = 0.3f, spread = 0.5f, fold, lag, lastBodyYaw;
    private long last;
    private final long start = System.nanoTime();

    // текущее крыло: корень и оси плоскости крыла в координатах тела (вправо, вверх, вперёд)
    private double sR, sU, sF, uR, uU, uF, nR, nU, nF;
    private int side;
    private final double[][] tips = new double[4][2];

    public Wings3D() {
        super("wings_3d", "Wings 3D", "Объёмные крылья: перья, дракон, энергия, механика. Машут от движения.", Category.WINGS);
        instance = this;
    }

    private static final int[] P_DRAGON = {0xFF3A1A2A, 0xFF8A2A3A, 0xFFE05A4A};
    private static final int[] P_ENERGY = {0xFF7FD8FF, 0xFFB8B4F2, 0xFFFFFFFF};
    private static final int[] P_MECH = {0xFF3A3F4A, 0xFF8A93A6, 0xFF4FD8FF};
    private static final int[] P_ANGEL = {0xFFFFFFFF, 0xFFE6E2F6, 0xFFFFF4C8};

    /** Палитры — константы: в кадре ничего не создаётся. */
    private int[] palette() {
        return switch (style.getId()) {
            case "dragon" -> P_DRAGON;
            case "energy" -> P_ENERGY;
            case "mech" -> P_MECH;
            default -> P_ANGEL;
        };
    }

    private int col(int preset, float t) {
        int c = colorMode.getId().equals("preset") ? preset : ColorFx.pick(colorMode.getId(), color.get(), color2.get(), t);
        c = Colors.shade(c, brightness.getF() / 100f);
        return Colors.mulAlpha(c, opacity.getF() / 100f);
    }

    /** Включён ли модуль — для слоя модели игрока. */
    public static boolean active() {
        Wings3D w = instance;
        return w != null && w.isEnabled();
    }

    /**
     * Из {@link dev.mraz1visual.render.PlayerLayer}: крылья рисуются у самой модели игрока —
     * поэтому видны от 3-го лица, в инвентаре и в превью меню, а приседание и элитры учитывает поза тела.
     * Обновление состояния — по реальному времени: второй вызов за кадр (инвентарь + мир) почти ничего не добавляет.
     */
    public static void submit(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector c, boolean gui) {
        Wings3D w = instance;
        if (w == null || !w.isEnabled()) return;
        w.update();
        WorldDraw.submitLocal(ps, c, gui, w.painter);
    }

    private final WorldDraw.Painter painter = this::paint;

    private void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;

        // состояние движения → целевые частота, амплитуда и раскрытие (плавно, по времени кадра)
        double vx = p.getX() - p.xo, vz = p.getZ() - p.zo;
        double horiz = Math.sqrt(vx * vx + vz * vz) * 20; // блоков в секунду
        boolean gliding = p.isFallFlying();
        boolean air = !p.onGround() && !gliding;
        double vy = p.getY() - p.yo;
        float freq, ampT, spreadT, foldT = p.isCrouching() ? 1f : 0f;
        if (gliding) { freq = 1.6f; ampT = 0.45f; spreadT = 1f; }                 // элитры: мощные взмахи
        else if (air && vy > 0.02) { freq = 2.6f; ampT = 0.7f; spreadT = 0.85f; } // прыжок: раскрытие
        else if (air) { freq = 7.5f; ampT = 0.16f; spreadT = 1f; }                // падение: широко и трепет
        else if (horiz > 4) { freq = 1.4f; ampT = 0.1f; spreadT = 0.18f; }        // бег: прижаты к спине
        else if (horiz > 0.5) { freq = 1.0f; ampT = 0.2f; spreadT = 0.4f; }       // ходьба
        else { freq = 0.45f; ampT = 0.12f; spreadT = 0.5f; }                      // покой: лёгкое дыхание
        // инерция: при повороте корпуса крылья отстают (плавно, по скорости поворота)
        float bodyYaw = p.yBodyRot;
        float dYaw = ((bodyYaw - lastBodyYaw) % 360f + 540f) % 360f - 180f;
        lastBodyYaw = bodyYaw;
        float yawRate = dt > 0 ? dYaw / dt : 0f;
        lag += (Math.max(-1f, Math.min(1f, yawRate / 360f)) - lag) * Math.min(1f, dt * 6f);
        float k = Math.min(1f, dt * 4f);
        amp += (ampT - amp) * k;
        spread += (spreadT - spread) * k;
        fold += (foldT - fold) * k;
        phase += dt * freq * speed.getF() * (float) Math.PI * 2;
    }

    private void paint(WorldDraw d) {
        frame.localBody();
        float time = (System.nanoTime() - start) / 1e9f;
        if (glow.get() && (style.getId().equals("energy") || style.getId().equals("mech"))) {
            // мягкий ореол за спиной — рисуется первым, под крыльями
            d.glowBegin();
            int gc = col(palette()[0], 0.5f);
            d.billboardDot(frame.wx(0, 0.1, -0.35), frame.wy(0, 0.1, -0.35), frame.wz(0, 0.1, -0.35),
                    1.1 * size.get(), Colors.withAlpha(gc, Math.round(40 * opacity.getF() / 100f)), 24);
            d.glowEnd();
        }
        for (side = -1; side <= 1; side += 2) {
            setupWing();
            switch (style.getId()) {
                case "dragon" -> dragon(d, time);
                case "energy" -> energy(d, time);
                case "mech" -> mech(d, time);
                default -> angel(d, time);
            }
        }
    }

    /** Плоскость крыла: размах вбок-назад под углом взмаха, слегка наклонена вверх. */
    private void setupWing() {
        double flap = Math.sin(phase) * amp;
        // крыло со стороны поворота прижимается, другое раскрывается — отставание по инерции
        double open = Math.toRadians(15 + 55 * spread * (1 - fold * 0.8)) + flap * 0.9 + side * lag * 0.35;
        double lift = 0.25 + flap * 0.6;
        // направление размаха
        sR = side * Math.cos(open) * Math.cos(lift);
        sU = Math.sin(lift);
        sF = -Math.sin(open) * Math.cos(lift);
        // «вверх» крыла — почти вертикально
        uR = 0; uU = 1; uF = -0.15;
        double l = Math.sqrt(uU * uU + uF * uF);
        uU /= l; uF /= l;
        // нормаль = s × u
        nR = sU * uF - sF * uU;
        nU = sF * uR - sR * uF;
        nF = sR * uU - sU * uR;
    }

    // точка крыла (s — вдоль размаха, u — вверх, n — толщина) → мир
    private double px(double s, double u, double n) { return frame.wx(side * 0.07 + sR * s + uR * u + nR * n, 0.08 + sU * s + uU * u + nU * n, -0.18 + sF * s + uF * u + nF * n); }
    private double py(double s, double u, double n) { return frame.wy(side * 0.07 + sR * s + uR * u + nR * n, 0.08 + sU * s + uU * u + nU * n, -0.18 + sF * s + uF * u + nF * n); }
    private double pz(double s, double u, double n) { return frame.wz(side * 0.07 + sR * s + uR * u + nR * n, 0.08 + sU * s + uU * u + nU * n, -0.18 + sF * s + uF * u + nF * n); }

    private void quadW(WorldDraw d, double s1, double u1, double s2, double u2, double s3, double u3, double s4, double u4, double n, int c1, int c2) {
        d.quad(px(s1, u1, n), py(s1, u1, n), pz(s1, u1, n), px(s2, u2, n), py(s2, u2, n), pz(s2, u2, n),
                px(s3, u3, n), py(s3, u3, n), pz(s3, u3, n), px(s4, u4, n), py(s4, u4, n), pz(s4, u4, n), c1, c1, c2, c2);
    }

    // ── ангельские: три ряда перьев с толщиной ───────────────

    private void angel(WorldDraw d, float time) {
        int[] pal = palette();
        double S = 1.25 * size.get();
        // верхняя «кость» крыла
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * S, b = (i + 1) / 6.0 * S;
            double ua = 0.22 * Math.sin(Math.PI * i / 6.0) * size.get(), ub = 0.22 * Math.sin(Math.PI * (i + 1) / 6.0) * size.get();
            int c = col(pal[1], i / 6f);
            quadW(d, a, ua + 0.05, b, ub + 0.05, b, ub - 0.04, a, ua - 0.04, 0.02, c, c);
            quadW(d, a, ua + 0.05, b, ub + 0.05, b, ub - 0.04, a, ua - 0.04, -0.02, Colors.shade(c, 0.85f), Colors.shade(c, 0.85f));
        }
        // ряды перьев: маховые (длинные), второй ряд, кроющие (короткие)
        feathers(d, S, 11, 0.95, 0.06, pal[0], pal[1], time, 0.0);
        feathers(d, S, 9, 0.62, 0.075, pal[1], pal[0], time, 0.012);
        feathers(d, S * 0.85, 8, 0.35, 0.085, pal[0], pal[1], time, 0.024);
    }

    private void feathers(WorldDraw d, double S, int count, double len, double width, int c1, int c2, float time, double n) {
        double L = len * size.get(), W = width * size.get();
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double s0 = t * S;
            double u0 = 0.2 * Math.sin(Math.PI * t) * size.get() - 0.02;
            // перо направлено вниз и наружу, кончики чуть колышутся
            double ang = Math.toRadians(-100 + 55 * t) + Math.sin(time * 2.2 + i) * 0.03 * (1 + amp);
            double ds = Math.cos(ang) * L * (0.75 + 0.25 * t), du = Math.sin(ang) * L * (0.75 + 0.25 * t);
            double ws = -Math.sin(ang) * W, wu = Math.cos(ang) * W;
            int ci = col(c1, (float) t), co = col(c2, (float) t + 0.2f);
            // лицевая и тыльная стороны — объём
            d.quad(px(s0 - ws, u0 - wu, n), py(s0 - ws, u0 - wu, n), pz(s0 - ws, u0 - wu, n),
                    px(s0 + ws, u0 + wu, n), py(s0 + ws, u0 + wu, n), pz(s0 + ws, u0 + wu, n),
                    px(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, n), py(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, n), pz(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, n),
                    px(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, n), py(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, n), pz(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, n),
                    ci, ci, co, co);
            double nb = n - 0.012;
            int cb = Colors.shade(ci, 0.78f), cbo = Colors.shade(co, 0.78f);
            d.quad(px(s0 + ws, u0 + wu, nb), py(s0 + ws, u0 + wu, nb), pz(s0 + ws, u0 + wu, nb),
                    px(s0 - ws, u0 - wu, nb), py(s0 - ws, u0 - wu, nb), pz(s0 - ws, u0 - wu, nb),
                    px(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, nb), py(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, nb), pz(s0 + ds - ws * 0.35, u0 + du - wu * 0.35, nb),
                    px(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, nb), py(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, nb), pz(s0 + ds + ws * 0.35, u0 + du + wu * 0.35, nb),
                    cb, cb, cbo, cbo);
            // стержень пера
            d.line(px(s0, u0, n + 0.004), py(s0, u0, n + 0.004), pz(s0, u0, n + 0.004),
                    px(s0 + ds, u0 + du, n + 0.004), py(s0 + ds, u0 + du, n + 0.004), pz(s0 + ds, u0 + du, n + 0.004),
                    0.008, Colors.shade(ci, 0.7f));
        }
    }

    // ── драконьи: кости-пальцы и полупрозрачная перепонка ────

    private void dragon(WorldDraw d, float time) {
        int[] pal = palette();
        double S = 1.3 * size.get();
        double ex = 0.42 * S, ey = 0.32 * S;        // локоть
        double wx = 0.78 * S, wy = 0.42 * S;        // запястье
        int bone = col(pal[0], 0f);
        boneSeg(d, 0, 0, ex, ey, 0.05, bone);
        boneSeg(d, ex, ey, wx, wy, 0.04, bone);
        // коготь на запястье
        boneSeg(d, wx, wy, wx + 0.08 * S, wy + 0.12 * S, 0.02, Colors.shade(bone, 1.3f));
        // пальцы: от запястья вниз-наружу, слегка «дышат» при взмахе
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(-20 - i * 28) + Math.sin(phase) * amp * 0.15;
            double len = (0.62 - i * 0.07) * S;
            tips[i][0] = wx + Math.cos(ang) * len;
            tips[i][1] = wy + Math.sin(ang) * len;
            boneSeg(d, wx, wy, tips[i][0], tips[i][1], 0.022, bone);
        }
        // перепонка между пальцами, с градиентом от корня к краю
        int mIn = Colors.mulAlpha(col(pal[1], 0f), 0.85f), mOut = Colors.mulAlpha(col(pal[2], 0.5f), 0.7f);
        double prevS = wx, prevU = wy;
        for (int i = 0; i < 4; i++) {
            double sA = i == 0 ? wx + 0.1 * S : tips[i - 1][0], uA = i == 0 ? wy + 0.25 * S : tips[i - 1][1];
            double sB = tips[i][0], uB = tips[i][1];
            double sag = 0.06 * S;
            d.tri(px(wx, wy, 0), py(wx, wy, 0), pz(wx, wy, 0),
                    px(sA, uA, 0), py(sA, uA, 0), pz(sA, uA, 0),
                    px((sA + sB) / 2, (uA + uB) / 2 + sag, 0), py((sA + sB) / 2, (uA + uB) / 2 + sag, 0), pz((sA + sB) / 2, (uA + uB) / 2 + sag, 0),
                    mIn, mOut, mOut);
            d.tri(px(wx, wy, 0), py(wx, wy, 0), pz(wx, wy, 0),
                    px((sA + sB) / 2, (uA + uB) / 2 + sag, 0), py((sA + sB) / 2, (uA + uB) / 2 + sag, 0), pz((sA + sB) / 2, (uA + uB) / 2 + sag, 0),
                    px(sB, uB, 0), py(sB, uB, 0), pz(sB, uB, 0),
                    mIn, mOut, mOut);
            prevS = sB;
            prevU = uB;
        }
        // нижняя перепонка: от последнего пальца к корню и локтю
        d.tri(px(0, 0, 0), py(0, 0, 0), pz(0, 0, 0), px(ex, ey, 0), py(ex, ey, 0), pz(ex, ey, 0), px(prevS, prevU, 0), py(prevS, prevU, 0), pz(prevS, prevU, 0), mIn, mIn, mOut);
        d.tri(px(ex, ey, 0), py(ex, ey, 0), pz(ex, ey, 0), px(wx, wy, 0), py(wx, wy, 0), pz(wx, wy, 0), px(prevS, prevU, 0), py(prevS, prevU, 0), pz(prevS, prevU, 0), mIn, mIn, mOut);
        if (glow.get()) {
            // светящиеся прожилки
            d.glowBegin();
            int vein = Colors.withAlpha(col(pal[2], 0.3f), Math.round(90 * opacity.getF() / 100f * (0.7f + 0.3f * (float) Math.sin(time * 2))));
            for (double[] t : tips) d.line(px(wx, wy, 0.01), py(wx, wy, 0.01), pz(wx, wy, 0.01), px(t[0], t[1], 0.01), py(t[0], t[1], 0.01), pz(t[0], t[1], 0.01), 0.03, vein);
            d.glowEnd();
        }
    }

    private void boneSeg(WorldDraw d, double s1, double u1, double s2, double u2, double w, int c) {
        // кость — две пересечённые полосы (видно с любой стороны)
        d.line(px(s1, u1, 0), py(s1, u1, 0), pz(s1, u1, 0), px(s2, u2, 0), py(s2, u2, 0), pz(s2, u2, 0), w, c);
        d.line(px(s1, u1, 0.015), py(s1, u1, 0.015), pz(s1, u1, 0.015), px(s2, u2, 0.015), py(s2, u2, 0.015), pz(s2, u2, 0.015), w * 0.8, Colors.shade(c, 0.8f));
    }

    // ── энергетические: слои свечения и ядро ─────────────────

    private static final double[][] ENERGY = {{0, 0}, {0.2, 0.34}, {0.5, 0.55}, {0.82, 0.62}, {1.0, 0.48}, {0.9, 0.18},
            {0.75, -0.08}, {0.58, -0.3}, {0.38, -0.46}, {0.18, -0.4}, {0.05, -0.16}};

    private void energy(WorldDraw d, float time) {
        int[] pal = palette();
        double S = 1.2 * size.get(), H = 0.95 * size.get();
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 2.4);
        d.glowBegin();
        for (int layer = 0; layer < 3; layer++) {
            double sc = 1 + layer * 0.07;
            float a = (0.5f - layer * 0.15f) * pulse;
            int n = ENERGY.length;
            double cs = 0.45 * S, cu = 0.08 * H;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int ci = Colors.mulAlpha(col(pal[0], i / (float) n), a);
                int co = Colors.mulAlpha(col(pal[1], j / (float) n + time * 0.05f), a * 0.4f);
                d.tri(px(cs, cu, 0), py(cs, cu, 0), pz(cs, cu, 0),
                        px(ENERGY[i][0] * S * sc, ENERGY[i][1] * H * sc, 0), py(ENERGY[i][0] * S * sc, ENERGY[i][1] * H * sc, 0), pz(ENERGY[i][0] * S * sc, ENERGY[i][1] * H * sc, 0),
                        px(ENERGY[j][0] * S * sc, ENERGY[j][1] * H * sc, 0), py(ENERGY[j][0] * S * sc, ENERGY[j][1] * H * sc, 0), pz(ENERGY[j][0] * S * sc, ENERGY[j][1] * H * sc, 0),
                        Colors.mulAlpha(ci, 0.35f), co, co);
            }
        }
        // яркий контур и «жилы» от корня
        int n = ENERGY.length;
        int edge = Colors.withAlpha(col(pal[2], 0f), Math.round(220 * opacity.getF() / 100f * pulse));
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            d.line(px(ENERGY[i][0] * S, ENERGY[i][1] * H, 0), py(ENERGY[i][0] * S, ENERGY[i][1] * H, 0), pz(ENERGY[i][0] * S, ENERGY[i][1] * H, 0),
                    px(ENERGY[j][0] * S, ENERGY[j][1] * H, 0), py(ENERGY[j][0] * S, ENERGY[j][1] * H, 0), pz(ENERGY[j][0] * S, ENERGY[j][1] * H, 0), 0.03, edge);
        }
        for (int i = 2; i < n - 1; i += 2) {
            float f = (float) ((time * 0.8 + i * 0.13) % 1.0);
            double ts = ENERGY[i][0] * S * f, tu = ENERGY[i][1] * H * f;
            d.line(px(0, 0, 0), py(0, 0, 0), pz(0, 0, 0), px(ENERGY[i][0] * S, ENERGY[i][1] * H, 0), py(ENERGY[i][0] * S, ENERGY[i][1] * H, 0), pz(ENERGY[i][0] * S, ENERGY[i][1] * H, 0),
                    0.012, Colors.mulAlpha(edge, 0.45f));
            // бегущий по жиле огонёк
            d.billboardDot(px(ts, tu, 0), py(ts, tu, 0), pz(ts, tu, 0), 0.05, Colors.mulAlpha(edge, 1f - f), 8);
        }
        d.glowEnd();
    }

    // ── механические: пластины веером и светящиеся швы ───────

    private void mech(WorldDraw d, float time) {
        int[] pal = palette();
        double S = 1.15 * size.get();
        int plates = 6;
        for (int i = 0; i < plates; i++) {
            double t = i / (double) (plates - 1);
            // каждая пластина раскрывается чуть позже предыдущей — «механика»
            double ang = Math.toRadians(20 - t * 95 * (0.6 + 0.4 * spread)) + Math.sin(phase - i * 0.35) * amp * 0.12;
            double len = (0.95 - Math.abs(t - 0.35) * 0.5) * S, w = 0.085 * size.get();
            double ds = Math.cos(ang), du = Math.sin(ang);
            double ns = -du * w, nu = ds * w;
            int c = col(Colors.lerp(pal[0], pal[1], (float) t), (float) t);
            // пластина с толщиной: лицо, тыл и торцы
            for (int f = 0; f < 2; f++) {
                double nOff = f == 0 ? 0.02 : -0.02;
                int cc = f == 0 ? c : Colors.shade(c, 0.7f);
                d.quad(px(ns, nu, nOff), py(ns, nu, nOff), pz(ns, nu, nOff),
                        px(ds * len + ns * 0.6, du * len + nu * 0.6, nOff), py(ds * len + ns * 0.6, du * len + nu * 0.6, nOff), pz(ds * len + ns * 0.6, du * len + nu * 0.6, nOff),
                        px(ds * len - ns * 0.6, du * len - nu * 0.6, nOff), py(ds * len - ns * 0.6, du * len - nu * 0.6, nOff), pz(ds * len - ns * 0.6, du * len - nu * 0.6, nOff),
                        px(-ns, -nu, nOff), py(-ns, -nu, nOff), pz(-ns, -nu, nOff), cc, cc, cc, cc);
            }
            if (glow.get()) {
                d.glowBegin();
                float pulse = 0.6f + 0.4f * (float) Math.sin(time * 3 - i * 0.6);
                int seam = Colors.withAlpha(col(pal[2], (float) t), Math.round(200 * pulse * opacity.getF() / 100f));
                d.line(px(0, 0, 0.025), py(0, 0, 0.025), pz(0, 0, 0.025), px(ds * len * 0.95, du * len * 0.95, 0.025), py(ds * len * 0.95, du * len * 0.95, 0.025), pz(ds * len * 0.95, du * len * 0.95, 0.025), 0.018, seam);
                d.billboardDot(px(ds * len, du * len, 0.03), py(ds * len, du * len, 0.03), pz(ds * len, du * len, 0.03), 0.05, seam, 8);
                d.glowEnd();
            }
        }
        // шарнир у спины
        d.billboardDot(px(0, 0, 0.03), py(0, 0, 0.03), pz(0, 0, 0.03), 0.07, col(pal[1], 0f), 10);
    }
}
