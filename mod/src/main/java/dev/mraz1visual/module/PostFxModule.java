package dev.mraz1visual.module;

import dev.mraz1visual.compat.Mc;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.post.PostFx;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3fc;

/**
 * Mraz1 PostFX — встроенная пост-обработка без Iris: свечение (Dual Kawase), ACES/AgX, пресеты цвета,
 * SSAO-lite, лучи солнца, глубина резкости, туман, виньетка, зерно, аберрация, эффект под водой.
 * Небо по умолчанию не трогается. При включённом шейдерпаке Iris отключается сам, чтобы не накладываться.
 */
public final class PostFxModule extends Module {
    private static PostFxModule instance;

    /** Пресеты цвета: lift(rgb) · gamma(rgb) · gain(rgb) · насыщенность · контраст · свечение · виньетка · зерно · экспозиция. */
    private static final float[][] PRESET = {
            // iOS Day: чисто, чуть прохладно, мягкий контраст
            {0.00f, 0.00f, 0.01f, 1.00f, 1.00f, 1.02f, 1.02f, 1.02f, 1.04f, 1.10f, 1.04f, 0.45f, 0.18f, 0.05f, 1.02f},
            // Golden Hour: тёплые света, янтарные тени
            {0.03f, 0.01f, -0.01f, 1.04f, 1.00f, 0.95f, 1.08f, 1.00f, 0.88f, 1.10f, 1.06f, 0.85f, 0.35f, 0.10f, 1.04f},
            // Midnight Glass: глубокий синий, холодные блики
            {-0.01f, 0.00f, 0.04f, 0.96f, 1.00f, 1.06f, 0.92f, 0.98f, 1.10f, 0.92f, 1.10f, 0.85f, 0.40f, 0.08f, 0.96f},
            // Cinematic: teal & orange, плотный контраст
            {0.00f, 0.02f, 0.03f, 1.02f, 1.00f, 0.98f, 1.06f, 1.00f, 0.94f, 1.05f, 1.16f, 0.55f, 0.45f, 0.18f, 1.00f},
            // Frost: морозная бирюза, приглушённая насыщенность
            {0.02f, 0.03f, 0.05f, 0.98f, 1.00f, 1.04f, 0.94f, 1.00f, 1.08f, 0.82f, 1.02f, 0.60f, 0.25f, 0.06f, 1.03f},
            // Neon Night: сочный пурпур и циан, сильное свечение
            {0.02f, -0.01f, 0.035f, 1.00f, 0.98f, 1.03f, 1.05f, 0.96f, 1.08f, 1.18f, 1.08f, 1.30f, 0.40f, 0.10f, 0.98f},
            // Aurora: зелёно-фиолетовые тени, мягкое сияние
            {-0.01f, 0.03f, 0.03f, 1.00f, 1.02f, 1.00f, 1.02f, 1.04f, 1.06f, 1.12f, 1.05f, 0.90f, 0.30f, 0.06f, 1.00f},
            // Film 35: тёплая плёнка, приподнятые тени, зерно
            {0.05f, 0.04f, 0.02f, 1.02f, 1.00f, 0.97f, 1.02f, 0.99f, 0.92f, 0.86f, 0.94f, 0.35f, 0.40f, 0.45f, 1.02f},
            // Нейтральный: только эффекты, без цветокоррекции
            {0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.5f, 0.25f, 0.08f, 1f},
    };

    final EnumSetting preset = add(new EnumSetting("preset", "Пресет", 0,
            "ios_day", "iOS Day", "golden_hour", "Golden Hour", "midnight_glass", "Midnight Glass", "cinematic", "Cinematic",
            "frost", "Frost", "neon_night", "Neon Night", "aurora", "Aurora", "film35", "Film 35", "neutral", "Нейтральный")
            .group("Пресет"));
    final SliderSetting amount = add(new SliderSetting("amount", "Сила пресета", 70, 0, 100, 5, "%").group("Пресет"));
    final EnumSetting quality = add(new EnumSetting("quality", "Качество", 1, "low", "Низкое", "medium", "Среднее", "high", "Высокое").group("Пресет"));
    final EnumSetting tonemap = add(new EnumSetting("tonemap", "Тонмаппинг", 0, "aces", "ACES", "agx", "AgX", "none", "Нет").group("Пресет"));
    final BoolSetting keepSky = add(new BoolSetting("keep_sky", "Не трогать небо", true).group("Пресет"));

    final SliderSetting exposure = add(new SliderSetting("exposure", "Экспозиция", 1.0, 0.6, 1.6, 0.02, "×").group("Свет"));
    final SliderSetting bloom = add(new SliderSetting("bloom", "Свечение", 1.0, 0, 2, 0.05, "×").group("Свет"));
    final SliderSetting threshold = add(new SliderSetting("threshold", "Порог свечения", 0.72, 0.4, 0.95, 0.01, "").group("Свет"));
    final BoolSetting rays = add(new BoolSetting("rays", "Лучи солнца", true).group("Свет"));
    final SliderSetting raysStrength = add(new SliderSetting("rays_strength", "Сила лучей", 0.6, 0, 1.5, 0.05, "").group("Свет"));

    final BoolSetting ssao = add(new BoolSetting("ssao", "SSAO-lite (тени в углах)", true).group("Глубина"));
    final SliderSetting ssaoStrength = add(new SliderSetting("ssao_strength", "Сила SSAO", 0.6, 0, 1, 0.05, "").group("Глубина"));
    final BoolSetting dof = add(new BoolSetting("dof", "Глубина резкости", false).group("Глубина"));
    final SliderSetting dofStrength = add(new SliderSetting("dof_strength", "Размытие фона", 0.7, 0, 1, 0.05, "").group("Глубина"));
    final SliderSetting fog = add(new SliderSetting("fog", "Атмосферный туман", 0.2, 0, 1, 0.05, "").group("Глубина"));
    final BoolSetting underwater = add(new BoolSetting("underwater", "Эффект под водой", true).group("Глубина"));

    final SliderSetting vignette = add(new SliderSetting("vignette", "Виньетка", 1.0, 0, 2, 0.05, "×").group("Плёнка"));
    final SliderSetting grain = add(new SliderSetting("grain", "Зерно", 1.0, 0, 3, 0.05, "×").group("Плёнка"));
    final SliderSetting ca = add(new SliderSetting("ca", "Хроматическая аберрация", 0, 0, 1, 0.05, "").group("Плёнка"));

    private float water;

    public PostFxModule() {
        super("postfx", "Mraz1 PostFX", "Свои шейдеры без Iris: свечение, цвет, лучи, SSAO, глубина резкости.", Category.SHADERS);
        instance = this;
    }

    /** Кадр мира готов: заполнить параметры и наложить эффекты. Вызывается из GameRendererPostMixin. */
    public static void afterLevel(float partialTick) {
        PostFxModule m = instance;
        if (m == null || !m.isEnabled() || PostFx.failed() || IrisCompat.shaderPackInUse()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        m.fill(mc);
        PostFx.apply(0.05f, Mc.depthFar(mc));
    }

    /** Перед рукой: сохранить глубину мира для эффектов. */
    public static void beforeHand() {
        PostFxModule m = instance;
        if (m == null || !m.isEnabled() || PostFx.failed() || IrisCompat.shaderPackInUse()) return;
        if (Minecraft.getInstance().level == null) return;
        PostFx.captureDepth();
    }

    private void fill(Minecraft mc) {
        PostFx.Params p = PostFx.P;
        float[] pr = PRESET[preset.get()];
        float a = amount.getF() / 100f;
        p.liftR = pr[0] * a;
        p.liftG = pr[1] * a;
        p.liftB = pr[2] * a;
        p.gammaR = 1 + (pr[3] - 1) * a;
        p.gammaG = 1 + (pr[4] - 1) * a;
        p.gammaB = 1 + (pr[5] - 1) * a;
        p.gainR = 1 + (pr[6] - 1) * a;
        p.gainG = 1 + (pr[7] - 1) * a;
        p.gainB = 1 + (pr[8] - 1) * a;
        p.saturation = 1 + (pr[9] - 1) * a;
        p.contrast = 1 + (pr[10] - 1) * a;
        p.bloom = pr[11] * bloom.getF();
        p.vignette = pr[12] * vignette.getF();
        p.grain = pr[13] * grain.getF();
        p.exposure = (1 + (pr[14] - 1) * a) * exposure.getF();
        p.threshold = threshold.getF();
        p.tonemap = switch (tonemap.getId()) {
            case "agx" -> 2;
            case "none" -> 0;
            default -> 1;
        };
        p.ca = ca.getF();
        p.skyKeep = keepSky.get() ? 1f : 0f;
        int q = quality.get();
        p.bloomLevels = 4 + q;
        p.radius = 1.5f;
        p.aoSamples = ssao.get() ? (q == 0 ? 0 : q == 1 ? 4 : 8) : 0;
        p.ao = ssao.get() ? ssaoStrength.getF() : 0;
        p.raySamples = q == 0 ? 6 : q == 1 ? 10 : 20;
        p.dof = dof.get() ? dofStrength.getF() : 0;
        p.dofRange = 0.5f;
        p.fog = fog.getF();

        Camera cam = mc.gameRenderer.getMainCamera();
        boolean inWater = cam.getFluidInCamera() == FogType.WATER;
        water += ((underwater.get() && inWater ? 1f : 0f) - water) * 0.15f;
        p.water = water;
        // туман — цвета неба у горизонта (по времени суток), без лишних запросов к миру
        float day = dayLight(mc);
        p.fogR = 0.08f + 0.55f * day;
        p.fogG = 0.10f + 0.62f * day;
        p.fogB = 0.16f + 0.72f * day;
        sun(mc, cam, p);
        p.rays = rays.get() ? raysStrength.getF() * day : 0;
    }

    /** 1 — день, 0 — ночь. */
    private static float dayLight(Minecraft mc) {
        long t = Mc.dayTime(mc.level) % 24000L;
        double a = (t / 24000.0 - 0.25) * Math.PI * 2;
        return (float) Math.max(0, Math.min(1, Math.cos(a) * 1.5 + 0.35));
    }

    /** Солнце на экране: направление по времени суток, проекция через оси камеры и поле зрения. */
    private static void sun(Minecraft mc, Camera cam, PostFx.Params p) {
        long t = Mc.dayTime(mc.level) % 24000L;
        double a = (t / 24000.0 - 0.25) * Math.PI * 2; // 0 — полдень
        float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
        if (mc.level.dimensionType().hasFixedTime()) {
            p.sunVis = 0;
            return;
        }
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        float z = dx * f.x() + dy * f.y();
        if (z <= 0.05f || dy < -0.05f) {
            p.sunVis = 0;
            return;
        }
        float fov = (float) Math.toRadians(mc.options.fov().get());
        float tan = (float) Math.tan(fov / 2);
        float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
        float sx = -(dx * l.x() + dy * l.y()) / z / tan / aspect, sy = (dx * u.x() + dy * u.y()) / z / tan;
        p.sunX = sx * 0.5f + 0.5f;
        p.sunY = sy * 0.5f + 0.5f;
        float edge = Math.max(Math.abs(sx), Math.abs(sy));
        p.sunVis = Math.max(0, Math.min(1, (1.6f - edge) / 0.6f)) * Math.min(1, (z - 0.05f) * 4);
    }

    /** Iris: шейдерпак включён — свою пост-обработку не накладываем. Проверка раз в секунду, через API Iris. */
    static final class IrisCompat {
        private static long checkedAt;
        private static boolean inUse;
        private static java.lang.reflect.Method isInUse;
        private static Object api;
        private static boolean missing;

        static boolean shaderPackInUse() {
            if (missing) return false;
            long now = System.currentTimeMillis();
            if (now - checkedAt < 1000) return inUse;
            checkedAt = now;
            try {
                if (api == null) {
                    if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                        missing = true;
                        return false;
                    }
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    isInUse = c.getMethod("isShaderPackInUse");
                }
                inUse = (Boolean) isInUse.invoke(api);
            } catch (Throwable t) {
                missing = true;
                inUse = false;
            }
            return inUse;
        }
    }
}
