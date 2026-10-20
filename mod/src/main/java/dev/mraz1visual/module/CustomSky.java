package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.render.SkyTextures;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.menu.WallpaperScreen;
import dev.mraz1visual.wallpaper.WallpaperPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Живое небо — настоящий скайбокс: сфера вокруг камеры, повёрнута только вместе с миром (без смещения
 * за игроком, без параллакса и дрожания). Рисуется до блоков и без глубины — всё в мире поверх.
 * Источники: процедурное небо (облака, туманность, звёзды, северное сияние, отдельно день и ночь),
 * обои Wallpaper Engine или своя анимация из папки config/mraz1visual/sky/.
 */
public final class CustomSky extends Module {
    private static CustomSky instance;
    private static final float R = 8f;
    private static final int LON = 64, LAT = 24;
    private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("sky");

    final EnumSetting source = add(new EnumSetting("source", "Источник", 0,
            "procedural", "Живое небо", "wallpaper", "Обои Wallpaper Engine", "frames", "Свои кадры (папка sky)"));
    final EnumSetting day = add(new EnumSetting("day", "День", 0, "day_soft", "Мягкие облака", "day_sunset", "Закат", "day_pastel", "Пастель"));
    final EnumSetting night = add(new EnumSetting("night", "Ночь", 0, "night_nebula", "Туманность", "night_aurora", "Северное сияние", "night_stars", "Звёздная ночь"));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость облаков", 1, 0, 4, 0.05, "×"));
    final SliderSetting clouds = add(new SliderSetting("clouds", "Облака", 60, 0, 100, 5, " %"));
    final SliderSetting dayBright = add(new SliderSetting("day_bright", "Яркость днём", 90, 20, 100, 5, " %"));
    final SliderSetting nightBright = add(new SliderSetting("night_bright", "Яркость ночью", 80, 20, 100, 5, " %"));
    final SliderSetting comfort = add(new SliderSetting("comfort", "Комфорт для глаз", 30, 0, 100, 5, " %"));
    final SliderSetting horizon = add(new SliderSetting("horizon", "Мягкость горизонта", 60, 0, 100, 5, " %"));
    final SliderSetting fps = add(new SliderSetting("frames_fps", "Скорость кадров (папка)", 12, 1, 60, 1, " к/с"));
    final SliderSetting repeats = add(new SliderSetting("repeats", "Повторов обоев по кругу", 2, 1, 6, 1, ""));
    final BoolSetting overworldOnly = add(new BoolSetting("overworld_only", "Только в обычном мире", true));
    final ActionSetting pickWall = add(new ActionSetting("pick", "Обои (общие с меню)", "Выбрать ›",
            () -> Minecraft.getInstance().setScreen(new WallpaperScreen(Minecraft.getInstance().screen))));
    final ActionSetting openFolder = add(new ActionSetting("folder", "Папка своих кадров", "Открыть ›", CustomSky::openFolder));
    final ActionSetting reload = add(new ActionSetting("reload", "Перечитать папку", "Обновить ›", () -> { if (instance != null) instance.framesState = 0; }));

    // меш сферы: единичные направления и UV
    private static final float[][] DX = new float[LAT + 1][LON + 1], DY = new float[LAT + 1][LON + 1], DZ = new float[LAT + 1][LON + 1];
    private static final float[][] U = new float[LAT + 1][LON + 1], V = new float[LAT + 1][LON + 1];
    static {
        for (int j = 0; j <= LAT; j++) {
            double lat = Math.PI / 2 - Math.PI * j / LAT; // +90° … −90°
            for (int i = 0; i <= LON; i++) {
                double lon = Math.PI * 2 * i / LON;
                DX[j][i] = (float) (Math.cos(lat) * Math.sin(lon));
                DY[j][i] = (float) Math.sin(lat);
                DZ[j][i] = (float) (-Math.cos(lat) * Math.cos(lon));
                U[j][i] = i / (float) LON;
                V[j][i] = j / (float) LAT;
            }
        }
    }

    private static ByteBufferBuilder buffer;
    private static MultiBufferSource.BufferSource src;
    private final Matrix4f pose = new Matrix4f();

    // процедурные текстуры: готовятся в фоне, загружаются в кадре
    private String dayKey = "", nightKey = "";
    private Identifier dayTex, nightTex, cloudTex;
    private final AtomicReference<int[]> dayPending = new AtomicReference<>(), nightPending = new AtomicReference<>(), cloudPending = new AtomicReference<>();
    private volatile boolean cloudsRequested;

    // свои кадры
    private volatile int framesState; // 0 — не загружено, 1 — грузится, 2 — готово
    private final List<int[]> framePixels = new ArrayList<>();
    private final List<int[]> frameSize = new ArrayList<>();
    private final List<Identifier> frameTex = new ArrayList<>();
    private volatile String framesStatus = "";

    private float nightK = -1;
    private long last;
    private float t;

    public CustomSky() {
        super("custom_sky", "Custom Sky", "Живое небо: облака, туманность, звёзды, сияние — или свои обои и кадры.", Category.SKY);
        instance = this;
    }

    public static void register() {
        WorldRenderEvents.START_MAIN.register(ctx -> {
            CustomSky s = instance;
            if (s == null || !s.isEnabled()) return;
            try {
                s.render();
            } catch (Throwable e) {
                ModuleManager.LOG.error("Custom sky failed, disabling", e);
                s.setEnabled(false);
            }
        });
    }

    private static void openFolder() {
        try {
            Files.createDirectories(FOLDER);
        } catch (Exception ignored) {
        }
        dev.mraz1visual.overlay.Screenshots.openExternal(FOLDER.toString());
    }

    // ── кадр ─────────────────────────────────────────────────

    private void render() {
        fogK = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (overworldOnly.get() && mc.level.dimension() != Level.OVERWORLD) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        t += dt;
        float comfortK = comfort.getF() / 100f;

        // день ↔ ночь — плавно, секунд за 8 (с комфортом — ещё мягче), без скачков
        float target = mc.level.isDarkOutside() ? 1f : 0f;
        if (nightK < 0) nightK = target;
        nightK += (target - nightK) * Math.min(1f, dt * 0.15f * (1f - 0.5f * comfortK));

        if (buffer == null) {
            buffer = new ByteBufferBuilder(1 << 18);
            src = MultiBufferSource.immediate(buffer);
        }
        // комфорт: потолок яркости и меньше контраста
        float cap = 1f - 0.45f * comfortK;
        float bright = Math.min(cap, (dayBright.getF() * (1 - nightK) + nightBright.getF() * nightK) / 100f);

        int horizonRgb = -1;
        switch (source.getId()) {
            case "wallpaper" -> wallpaper(bright);
            case "frames" -> {
                frames(bright);
                if (!frameTex.isEmpty()) horizonRgb = framesHorizon;
            }
            default -> {
                procedural(bright, comfortK);
                horizonRgb = Colors.lerp(SkyTextures.horizon(dayKey), SkyTextures.horizon(nightKey), nightK);
            }
        }
        int veil = Math.round(90 * comfortK);
        if (comfortK > 0.01f) {
            // мягкая «вуаль» среднего тона — снижает контраст неба
            sphere(src.getBuffer(RenderTypes.textSeeThrough(SkyTextures.white())), veil << 24 | VEIL, 0f, 1f);
        }
        src.endBatch();

        // туман у края прорисовки — в цвет нашего горизонта (с той же яркостью и вуалью): без светлого/тёмного шва
        if (horizonRgb != -1) {
            int c = Colors.shade(horizonRgb, bright);
            if (veil > 0) c = Colors.lerp(c, 0xFF000000 | VEIL, veil / 255f);
            fogRgb = c;
            fogK = 0.85f;
        }
    }

    private static final int VEIL = 0x5A6070;
    /** Цвет и сила подкраски тумана в этом кадре (0 — неба нет, туман ванильный). */
    private int fogRgb;
    private float fogK;
    private volatile int framesHorizon = 0xFF808890;

    /** Из миксина FogRenderer: ванильный туман → к цвету горизонта живого неба. Под водой, в лаве, при слепоте — не трогаем. */
    public static org.joml.Vector4f fog(org.joml.Vector4f v) {
        CustomSky s = instance;
        if (s == null || !s.isEnabled() || v == null || s.fogK <= 0) return v;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return v;
        if (mc.player != null && (mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || mc.player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) return v;
        int c = s.fogRgb;
        float k = s.fogK;
        return v.set(v.x() + ((c >> 16 & 255) / 255f - v.x()) * k, v.y() + ((c >> 8 & 255) / 255f - v.y()) * k,
                v.z() + ((c & 255) / 255f - v.z()) * k, v.w());
    }

    // ── процедурное ──────────────────────────────────────────

    private void procedural(float bright, float comfortK) {
        ensureProcedural();
        int b = Math.round(bright * 255);
        int rgb = b << 16 | b << 8 | b;
        float dayA = 1 - nightK;
        if (dayTex != null && dayA > 0.01f) sphere(src.getBuffer(RenderTypes.textSeeThrough(dayTex)), Math.round(255 * dayA) << 24 | rgb, 0f, 1f);
        if (nightTex != null && nightK > 0.01f) sphere(src.getBuffer(RenderTypes.textSeeThrough(nightTex)), Math.round(255 * nightK) << 24 | rgb, 0f, 1f);
        if (cloudTex != null && clouds.get() > 0) {
            // облака медленно плывут: вращаем саму сферу облаков, UV не трогаем — без шва
            float rot = t * 0.004f * speed.getF();
            float ca = clouds.getF() / 100f * (1 - nightK * 0.75f);
            int cb = Math.round(bright * (1 - nightK * 0.6f) * 255);
            band(src.getBuffer(RenderTypes.textSeeThrough(cloudTex)), Math.round(255 * ca) << 24 | cb << 16 | cb << 8 | cb, rot, 0.02f, 0.6f, 1);
            band(src.getBuffer(RenderTypes.textSeeThrough(cloudTex)), Math.round(170 * ca) << 24 | cb << 16 | cb << 8 | cb, -rot * 0.6f + 0.3f, 0.1f, 0.85f, 2);
        }
        if (nightK > 0.01f) {
            VertexConsumer w = src.getBuffer(RenderTypes.textSeeThrough(SkyTextures.white()));
            twinkle(w, bright);
            if (night.getId().equals("night_aurora")) aurora(w, bright);
        }
    }

    private void ensureProcedural() {
        if (!day.getId().equals(dayKey)) {
            dayKey = day.getId();
            String k = dayKey;
            Thread th = new Thread(() -> dayPending.set(SkyTextures.base(k)), "Mraz1Visual sky");
            th.setDaemon(true);
            th.start();
        }
        if (!night.getId().equals(nightKey)) {
            nightKey = night.getId();
            String k = nightKey;
            Thread th = new Thread(() -> nightPending.set(SkyTextures.base(k)), "Mraz1Visual sky");
            th.setDaemon(true);
            th.start();
        }
        if (!cloudsRequested) {
            cloudsRequested = true;
            Thread th = new Thread(() -> cloudPending.set(SkyTextures.clouds(4242)), "Mraz1Visual sky");
            th.setDaemon(true);
            th.start();
        }
        int[] p = dayPending.getAndSet(null);
        if (p != null) { SkyTextures.release(dayTex); dayTex = SkyTextures.upload(p, SkyTextures.W, SkyTextures.H, "day"); }
        p = nightPending.getAndSet(null);
        if (p != null) { SkyTextures.release(nightTex); nightTex = SkyTextures.upload(p, SkyTextures.W, SkyTextures.H, "night"); }
        p = cloudPending.getAndSet(null);
        if (p != null) { SkyTextures.release(cloudTex); cloudTex = SkyTextures.upload(p, SkyTextures.CW, SkyTextures.CH, "clouds"); }
    }

    /**
     * Мерцающие звёзды поверх текстуры: мягкие точки (яркий центр, прозрачный край — без «зубчатых» квадратов,
     * которые рябят при повороте камеры), яркость плавно «дышит». Комфорт для глаз уменьшает мерцание.
     */
    private void twinkle(VertexConsumer vc, float bright) {
        float calm = 1f - 0.7f * comfort.getF() / 100f;
        for (int i = 0; i < 140; i++) {
            float h1 = frac(i * 0.6180339f), h2 = frac(i * 0.4142135f + 0.17f);
            double lon = h1 * Math.PI * 2, lat = 0.15 + h2 * 1.3;
            float wave = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + h2 * 1.6f) + i * 1.7f);
            float tw = 1f - (1f - wave * wave) * calm;
            int a = Math.round(255 * nightK * tw * bright);
            if (a < 6) continue;
            float x = (float) (Math.cos(lat) * Math.sin(lon)), y = (float) Math.sin(lat), z = (float) (-Math.cos(lat) * Math.cos(lon));
            float s = 0.014f + 0.012f * h1;
            // касательные к сфере: «вправо» по долготе и «вверх» по широте
            float ex = (float) Math.cos(lon), ez = (float) Math.sin(lon);
            float ux = (float) (-Math.sin(lat) * Math.sin(lon)), uy = (float) Math.cos(lat), uz = (float) (Math.sin(lat) * Math.cos(lon));
            int c = a << 24 | 0xFFF6E8, e = 0xFFF6E8;
            float cx = x * R, cy = y * R, cz = z * R;
            for (int k = 0; k < 4; k++) {
                // четыре треугольника «центр → край → край»: точка с мягким краем
                float ax = k == 0 ? ex : k == 1 ? ux : k == 2 ? -ex : -ux, ay = k == 1 ? uy : k == 3 ? -uy : 0, az = k == 0 ? ez : k == 1 ? uz : k == 2 ? -ez : -uz;
                int k2 = (k + 1) & 3;
                float bx = k2 == 0 ? ex : k2 == 1 ? ux : k2 == 2 ? -ex : -ux, by = k2 == 1 ? uy : k2 == 3 ? -uy : 0, bz = k2 == 0 ? ez : k2 == 1 ? uz : k2 == 2 ? -ez : -uz;
                v(vc, cx, cy, cz, 0.5f, 0.5f, c);
                v(vc, cx + ax * s * R, cy + ay * s * R, cz + az * s * R, 1, 0, e);
                v(vc, cx + bx * s * R, cy + by * s * R, cz + bz * s * R, 1, 1, e);
                v(vc, cx, cy, cz, 0.5f, 0.5f, c);
            }
        }
    }

    /** Северное сияние: три волнистые «шторы» на севере, низ ярче, верх растворяется. */
    private void aurora(VertexConsumer vc, float bright) {
        int segs = 48;
        for (int layer = 0; layer < 3; layer++) {
            float baseLat = 0.18f + layer * 0.06f, height = 0.35f - layer * 0.06f;
            for (int i = 0; i < segs; i++) {
                float a0 = i / (float) segs, a1 = (i + 1) / (float) segs;
                double lon0 = Math.PI + (a0 - 0.5) * 2.4 + Math.sin(t * 0.25 + a0 * 7 + layer) * 0.08;
                double lon1 = Math.PI + (a1 - 0.5) * 2.4 + Math.sin(t * 0.25 + a1 * 7 + layer) * 0.08;
                float w0 = 0.5f + 0.5f * (float) Math.sin(t * 0.6 + a0 * 11 + layer * 2), w1 = 0.5f + 0.5f * (float) Math.sin(t * 0.6 + a1 * 11 + layer * 2);
                float edge0 = (float) Math.sin(a0 * Math.PI), edge1 = (float) Math.sin(a1 * Math.PI);
                int g0 = Math.round(150 * nightK * w0 * edge0 * bright), g1 = Math.round(150 * nightK * w1 * edge1 * bright);
                int col = layer == 1 ? 0x7AF0C8 : layer == 2 ? 0xB070F0 : 0x5AF09A;
                double la0 = baseLat + Math.sin(t * 0.4 + a0 * 5) * 0.03, la1 = baseLat + Math.sin(t * 0.4 + a1 * 5) * 0.03;
                double lt0 = la0 + height, lt1 = la1 + height;
                v(vc, px(la0, lon0), py(la0), pz(la0, lon0), 0, 0, g0 << 24 | col);
                v(vc, px(la1, lon1), py(la1), pz(la1, lon1), 1, 0, g1 << 24 | col);
                v(vc, px(lt1, lon1), py(lt1), pz(lt1, lon1), 1, 1, col);
                v(vc, px(lt0, lon0), py(lt0), pz(lt0, lon0), 0, 1, col);
            }
        }
    }

    private static float px(double lat, double lon) { return (float) (Math.cos(lat) * Math.sin(lon)) * R; }
    private static float py(double lat) { return (float) Math.sin(lat) * R; }
    private static float pz(double lat, double lon) { return (float) (-Math.cos(lat) * Math.cos(lon)) * R; }

    private static float frac(float x) { return x - (float) Math.floor(x); }

    // ── обои и свои кадры ────────────────────────────────────

    private void wallpaper(float bright) {
        WallpaperPlayer.SkyFrame f = WallpaperPlayer.skyFrame();
        if (f == null) return;
        int b = Math.round(bright * 255);
        int rgb = 0xFF000000 | b << 16 | b << 8 | b;
        // обои (16:9) — полосой вокруг горизонта, повторяясь по кругу; сфера мира, а не экрана
        int rep = Math.max(1, repeats.getI());
        float bandH = (float) (Math.PI * 2 / rep * f.h() / (float) f.w());
        float lat0 = -0.12f, lat1 = Math.min(1.45f, lat0 + bandH);
        cylinder(src.getBuffer(RenderTypes.textSeeThrough(f.id())), rgb, lat0, lat1, rep);
    }

    private void frames(float bright) {
        if (framesState == 0) loadFramesAsync();
        // по одной загрузке в кадр — без подвисаний
        synchronized (framePixels) {
            if (frameTex.size() < framePixels.size()) {
                int i = frameTex.size();
                int[] sz = frameSize.get(i);
                frameTex.add(SkyTextures.upload(framePixels.get(i), sz[0], sz[1], "frame"));
                framePixels.set(i, null);
            }
        }
        int n = frameTex.size();
        if (n == 0) return;
        int b = Math.round(bright * 255);
        int rgb = b << 16 | b << 8 | b;
        if (n == 1) {
            sphere(src.getBuffer(RenderTypes.textSeeThrough(frameTex.get(0))), 0xFF000000 | rgb, 0f, 1f);
            return;
        }
        // плавное зацикливание: текущий кадр + следующий с нарастающей прозрачностью
        float pos = t * fps.getF();
        int i = (int) Math.floor(pos) % n, j = (i + 1) % n;
        float k = pos - (float) Math.floor(pos);
        Identifier a = frameTex.get(i), bb = frameTex.get(j);
        if (a != null) sphere(src.getBuffer(RenderTypes.textSeeThrough(a)), 0xFF000000 | rgb, 0f, 1f);
        if (bb != null) sphere(src.getBuffer(RenderTypes.textSeeThrough(bb)), Math.round(255 * k) << 24 | rgb, 0f, 1f);
    }

    private void loadFramesAsync() {
        framesState = 1;
        synchronized (framePixels) {
            for (Identifier id : frameTex) SkyTextures.release(id);
            frameTex.clear();
            framePixels.clear();
            frameSize.clear();
        }
        Thread th = new Thread(() -> {
            try {
                Files.createDirectories(FOLDER);
                List<Path> files = new ArrayList<>();
                try (var s = Files.list(FOLDER)) {
                    s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpg|jpeg)$")).sorted().forEach(files::add);
                }
                int max = Math.min(files.size(), 240);
                for (int i = 0; i < max; i++) {
                    BufferedImage img = ImageIO.read(files.get(i).toFile());
                    if (img == null) continue;
                    int w = img.getWidth(), h = img.getHeight();
                    float k = Math.min(1f, Math.min(2048f / w, 1024f / h));
                    int tw = Math.max(2, Math.round(w * k)), thh = Math.max(2, Math.round(h * k));
                    if (k < 1f) {
                        BufferedImage s2 = new BufferedImage(tw, thh, BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g = s2.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(img, 0, 0, tw, thh, null);
                        g.dispose();
                        img = s2;
                    }
                    int[] px = img.getRGB(0, 0, tw, thh, null, 0, tw);
                    for (int p = 0; p < px.length; p++) px[p] |= 0xFF000000;
                    if (i == 0) framesHorizon = horizonAverage(px, tw, thh);
                    synchronized (framePixels) {
                        framePixels.add(px);
                        frameSize.add(new int[]{tw, thh});
                    }
                }
                framesStatus = max == 0 ? "Папка пуста" : "Кадров: " + max;
            } catch (Throwable e) {
                framesStatus = "Не удалось прочитать папку";
                ModuleManager.LOG.warn("Sky frames load failed", e);
            }
            framesState = 2;
        }, "Mraz1Visual sky frames");
        th.setDaemon(true);
        th.start();
    }

    /** Средний цвет полосы у горизонта (середина панорамы по высоте) — под него подстраивается туман. */
    private static int horizonAverage(int[] px, int w, int h) {
        long r = 0, g = 0, b = 0, n = 0;
        int y0 = Math.round(h * 0.45f), y1 = Math.max(y0 + 1, Math.round(h * 0.55f));
        for (int y = y0; y < y1 && y < h; y++) {
            for (int x = 0; x < w; x += 2) {
                int c = px[y * w + x];
                r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
                n++;
            }
        }
        if (n == 0) return 0xFF808890;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    // ── геометрия ────────────────────────────────────────────

    /** Альфа у горизонта: ниже линии горизонта небо растворяется в ванильный туман — без шва. */
    private float horizonAlpha(float dirY) {
        float soft = 0.04f + horizon.getF() / 100f * 0.22f;
        float x = (dirY + soft * 0.4f) / soft;
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Полная сфера с равнопромежуточной текстурой. */
    private void sphere(VertexConsumer vc, int argb, float rot, float alphaMul) {
        int a = argb >>> 24, rgb = argb & 0xFFFFFF;
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        for (int j = 0; j < LAT; j++) {
            for (int i = 0; i < LON; i++) {
                quad(vc, j, i, j + 1, i + 1, c, s, a, rgb, alphaMul);
            }
        }
    }

    /** Полоса облаков между широтами (в долях сферы по V), повёрнута на rot. */
    private void band(VertexConsumer vc, int argb, float rot, float vTop, float vBottom, int uRepeat) {
        int a = argb >>> 24, rgb = argb & 0xFFFFFF;
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        int j0 = Math.round(vTop * LAT), j1 = Math.round(vBottom * LAT);
        for (int j = j0; j < j1; j++) {
            float va = (j - j0) / (float) (j1 - j0), vb = (j + 1 - j0) / (float) (j1 - j0);
            for (int i = 0; i < LON; i++) {
                float ua = U[j][i] * uRepeat, ub = U[j][i + 1] * uRepeat;
                vert(vc, j, i, c, s, ua, va, a, rgb);
                vert(vc, j, i + 1, c, s, ub, va, a, rgb);
                vert(vc, j + 1, i + 1, c, s, ub, vb, a, rgb);
                vert(vc, j + 1, i, c, s, ua, vb, a, rgb);
            }
        }
    }

    /** Цилиндрическая полоса для обоев между широтами lat0..lat1, rep повторов по кругу (каждый второй — зеркально). */
    private void cylinder(VertexConsumer vc, int argb, float lat0, float lat1, int rep) {
        int a = argb >>> 24, rgb = argb & 0xFFFFFF;
        int rows = 12;
        for (int r = 0; r < rows; r++) {
            float la = lat0 + (lat1 - lat0) * r / rows, lb = lat0 + (lat1 - lat0) * (r + 1) / rows;
            float va = 1f - r / (float) rows, vb = 1f - (r + 1) / (float) rows;
            for (int i = 0; i < LON; i++) {
                double l0 = Math.PI * 2 * i / LON, l1 = Math.PI * 2 * (i + 1) / LON;
                float u0 = mirror(i / (float) LON * rep + 1e-4f), u1 = mirror((i + 1) / (float) LON * rep - 1e-4f);
                int ca = Math.round(a * horizonAlpha((float) Math.sin(la))), cb = Math.round(a * horizonAlpha((float) Math.sin(lb)));
                v(vc, px(la, l0), py(la), pz(la, l0), u0, va, ca << 24 | rgb);
                v(vc, px(la, l1), py(la), pz(la, l1), u1, va, ca << 24 | rgb);
                v(vc, px(lb, l1), py(lb), pz(lb, l1), u1, vb, cb << 24 | rgb);
                v(vc, px(lb, l0), py(lb), pz(lb, l0), u0, vb, cb << 24 | rgb);
            }
        }
    }

    private static float mirror(float u) {
        int k = (int) Math.floor(u);
        float f = u - k;
        return (k & 1) == 0 ? f : 1f - f;
    }

    private void quad(VertexConsumer vc, int j0, int i0, int j1, int i1, float c, float s, int a, int rgb, float alphaMul) {
        vert(vc, j0, i0, c, s, U[j0][i0], V[j0][i0], Math.round(a * alphaMul), rgb);
        vert(vc, j0, i1, c, s, U[j0][i1], V[j0][i1], Math.round(a * alphaMul), rgb);
        vert(vc, j1, i1, c, s, U[j1][i1], V[j1][i1], Math.round(a * alphaMul), rgb);
        vert(vc, j1, i0, c, s, U[j1][i0], V[j1][i0], Math.round(a * alphaMul), rgb);
    }

    private void vert(VertexConsumer vc, int j, int i, float c, float s, float u, float v, int a, int rgb) {
        float x = DX[j][i], y = DY[j][i], z = DZ[j][i];
        float rx = x * c - z * s, rz = x * s + z * c;
        int aa = Math.round(a * horizonAlpha(y));
        v(vc, rx * R, y * R, rz * R, u, v, aa << 24 | rgb);
    }

    private void v(VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setLight(0xF000F0);
    }
}
