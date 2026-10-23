package dev.mraz1visual.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Mraz1 PostFX без Iris: несколько полноэкранных проходов OpenGL сразу после отрисовки мира (до HUD и меню).
 * 1) копия кадра; 2) свечение — Dual Kawase вниз/вверх по пирамиде половинных текстур;
 * 3) мягкое размытие для глубины резкости и воды — две уменьшенные копии;
 * 4) композит в основной кадр (SSAO-lite, DOF, туман, лучи, тонмаппинг, пресет, аберрация, виньетка, зерно).
 * Состояние OpenGL, которое кэширует игра, меняется только через GlStateManager и возвращается как было.
 * В кадре ничего не выделяется: текстуры пересоздаются только при смене размера окна.
 */
public final class PostFx {
    private PostFx() {}

    /** Параметры кадра: заполняет модуль, читает композит. Один изменяемый объект — без мусора в кадре. */
    public static final class Params {
        public int bloomLevels = 4, aoSamples = 6, raySamples = 12, tonemap = 1;
        public float exposure = 1f, bloom = 0.6f, threshold = 0.72f, knee = 0.12f, radius = 1f;
        public float liftR, liftG, liftB, gammaR = 1, gammaG = 1, gammaB = 1, gainR = 1, gainG = 1, gainB = 1;
        public float saturation = 1, contrast = 1, vignette = 0.3f, grain = 0.15f, ca = 0f;
        public float ao = 0.5f, fog = 0f, fogR = 0.6f, fogG = 0.7f, fogB = 0.85f;
        public float sunX, sunY, sunVis, rays = 0.5f, dof = 0f, dofRange = 0.5f, water = 0f, skyKeep = 1f;
    }

    public static final Params P = new Params();
    /** Отладка стенда: до какой стадии выполнять (0 — ничего, 1 — копия, 2 — +глубина, 3 — +свечение, 9 — всё). */
    public static int stage = 9;

    private static final int MAX_LEVELS = 6;
    private static boolean failed;
    /** Копия глубины в R32F (её игра очистит перед рукой). */
    private static final String DEPTH_COPY = """
            #version 330 core
            uniform sampler2D uDepth;
            uniform float uReverse;
            in vec2 uv;
            out vec4 fragColor;
            void main() {
                float d = texture(uDepth, uv).r;
                fragColor = vec4(uReverse > 0.5 ? 1.0 - d : d);
            }
            """;
    private static int progDown, progUp, progComp, vao;
    private static int srcFbo, srcTexId = -1;
    private static int sceneTex, sceneFbo, depthTex, depthFbo, progDepth, pdDepth, pdReverse;
    /** Глубина мира сохранена в этом кадре (до очистки под руку). */
    private static boolean depthCaptured;
    private static final int[] downTex = new int[MAX_LEVELS], downFbo = new int[MAX_LEVELS];
    private static final int[] upTex = new int[MAX_LEVELS], upFbo = new int[MAX_LEVELS];
    private static final int[] lw = new int[MAX_LEVELS], lh = new int[MAX_LEVELS];
    private static final int[] blurTex = new int[2], blurFbo = new int[2];
    private static int baseW, baseH;
    private static long startNanos = System.nanoTime();

    // uniform-локации
    private static int dSrc, dTexel, dThreshold, dKnee, uSrc, uSame, uTexel, uRadius, uSameWeight;
    private static int cScene, cBloom, cDepth, cBlur, cRes, cTime, cNearFar, cExposure, cTonemap, cBloomS, cLift, cGamma, cGain,
            cSat, cContrast, cVignette, cGrain, cCA, cAO, cAOSamples, cFog, cFogColor, cSun, cRays, cRaySamples, cDof, cDofRange,
            cWater, cSkyKeep;

    // сохранённое состояние
    private static final int[] VIEWPORT = new int[4];

    public static boolean failed() {
        return failed;
    }

    /**
     * Сохранить глубину мира: игра очищает её перед рукой, а SSAO, туману, DOF и лучам она нужна.
     * Один проход в R32F, вызывается прямо перед очисткой в GameRenderer.renderLevel.
     */
    public static void captureDepth() {
        if (failed || stage < 2) return;
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int w = main.width, h = main.height;
            if (w < 16 || h < 16 || main.getDepthTexture() == null) return;
            if (progComp == 0) init();
            ensure(w, h);
            int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER), prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, 0);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(progDepth);
            GL20.glUniform1i(pdDepth, 0);
            // 26.2+: глубина перевёрнута (дальше = 0) — приводим к привычной, остальные проходы не меняются
            GL20.glUniform1f(pdReverse, dev.mraz1visual.compat.Mc.REVERSED_Z ? 1f : 0f);
            GlStateManager._bindTexture(((GlTexture) main.getDepthTexture()).glId());
            pass(depthFbo, w, h);
            finish(prevRead, prevDraw, w, h, 1);
            depthCaptured = true;
        } catch (Throwable t) {
            failed = true;
            ModuleManager.LOG.warn("Mraz1 PostFX disabled (depth)", t);
        }
    }

    /** Применить эффекты к кадру мира. Вызывается после GameRenderer.renderLevel. */
    public static void apply(float nearPlane, float farPlane) {
        if (failed || stage < 1) return;
        long cpu0 = System.nanoTime();
        try {
            if (profile) gpuBegin();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int w = main.width, h = main.height;
            if (w < 16 || h < 16 || main.getDepthTexture() == null) return;
            if (progComp == 0) init();
            ensure(w, h);
            int mainId = ((GlTexture) main.getColorTexture()).glId();
            int depthId = ((GlTexture) main.getDepthTexture()).glId();

            // ── сохранить ──
            int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER), prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
            for (int i = 0; i < 4; i++) GL33.glBindSampler(i, 0);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            //? if >=26.1 {
            /*GlStateManager._colorMask(0xF);
            *///?} else {
            GlStateManager._colorMask(true, true, true, true);
            //?}
            GL30.glBindVertexArray(vao);

            // ── 1. копия кадра ──
            if (mainId != srcTexId) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFbo);
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainId, 0);
                srcTexId = mainId;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFbo);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            if (stage < 3) { finish(prevRead, prevDraw, w, h, 4); return; }
            // ── 2. свечение: Dual Kawase ──
            int levels = Math.max(2, Math.min(MAX_LEVELS, P.bloomLevels));
            boolean bloom = P.bloom > 0.001f;
            if (bloom) {
                // половинный уровень — аппаратным уменьшением (дёшево), Kawase и порог — с четверти
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneFbo);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downFbo[0]);
                GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, lw[0], lh[0], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
                GL20.glUseProgram(progDown);
                GL20.glUniform1i(dSrc, 0);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                for (int i = 1; i < levels; i++) {
                    int srcTex = downTex[i - 1];
                    int sw = lw[i - 1], sh = lh[i - 1];
                    GlStateManager._bindTexture(srcTex);
                    GL20.glUniform2f(dTexel, 1f / sw, 1f / sh);
                    GL20.glUniform1f(dThreshold, i == 1 ? Math.max(0.0001f, P.threshold) : 0f);
                    GL20.glUniform1f(dKnee, P.knee);
                    pass(downFbo[i], lw[i], lh[i]);
                }
                GL20.glUseProgram(progUp);
                GL20.glUniform1i(uSrc, 0);
                GL20.glUniform1i(uSame, 1);
                GL20.glUniform1f(uRadius, P.radius);
                for (int i = levels - 2; i >= 1; i--) {
                    int smallTex = i == levels - 2 ? downTex[levels - 1] : upTex[i + 1];
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                    GlStateManager._bindTexture(smallTex);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE1);
                    GlStateManager._bindTexture(downTex[i]);
                    GL20.glUniform2f(uTexel, 1f / lw[i + 1], 1f / lh[i + 1]);
                    GL20.glUniform1f(uSameWeight, i == 1 ? 0f : 0.7f);
                    pass(upFbo[i], lw[i], lh[i]);
                }
            }

            // ── 3. мягкое размытие для DOF и воды: две уменьшенные копии ──
            boolean blur = P.dof > 0.001f || P.water > 0.001f;
            if (blur) {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneFbo);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[0]);
                GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, lw[0], lh[0], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, blurFbo[0]);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[1]);
                GlStateManager._glBlitFrameBuffer(0, 0, lw[0], lh[0], 0, 0, lw[1], lh[1], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }

            if (stage < 4) { finish(prevRead, prevDraw, w, h, 4); return; }
            // ── 4. композит в основной кадр ──
            GL20.glUseProgram(progComp);
            bindUnit(0, sceneTex);
            bindUnit(1, bloom ? (levels > 2 ? upTex[1] : downTex[1]) : sceneTex);
            bindUnit(2, depthCaptured ? depthTex : depthId);
            depthCaptured = false;
            bindUnit(3, blur ? blurTex[1] : sceneTex);
            GL20.glUniform1i(cScene, 0);
            GL20.glUniform1i(cBloom, 1);
            GL20.glUniform1i(cDepth, 2);
            GL20.glUniform1i(cBlur, 3);
            GL20.glUniform2f(cRes, w, h);
            GL20.glUniform1f(cTime, ((System.nanoTime() - startNanos) % 3_600_000_000_000L) / 1e9f);
            GL20.glUniform2f(cNearFar, nearPlane, farPlane);
            GL20.glUniform1f(cExposure, P.exposure);
            GL20.glUniform1i(cTonemap, P.tonemap);
            GL20.glUniform1f(cBloomS, bloom ? P.bloom : 0f);
            GL20.glUniform3f(cLift, P.liftR, P.liftG, P.liftB);
            GL20.glUniform3f(cGamma, P.gammaR, P.gammaG, P.gammaB);
            GL20.glUniform3f(cGain, P.gainR, P.gainG, P.gainB);
            GL20.glUniform1f(cSat, P.saturation);
            GL20.glUniform1f(cContrast, P.contrast);
            GL20.glUniform1f(cVignette, P.vignette);
            GL20.glUniform1f(cGrain, P.grain);
            GL20.glUniform1f(cCA, P.ca);
            GL20.glUniform1f(cAO, P.ao);
            GL20.glUniform1i(cAOSamples, P.aoSamples);
            GL20.glUniform1f(cFog, P.fog);
            GL20.glUniform3f(cFogColor, P.fogR, P.fogG, P.fogB);
            GL20.glUniform3f(cSun, P.sunX, P.sunY, P.sunVis);
            GL20.glUniform1f(cRays, P.rays);
            GL20.glUniform1i(cRaySamples, P.raySamples);
            GL20.glUniform1f(cDof, P.dof);
            GL20.glUniform1f(cDofRange, P.dofRange);
            GL20.glUniform1f(cWater, P.water);
            GL20.glUniform1f(cSkyKeep, P.skyKeep);
            pass(srcFbo, w, h);
            if (profile) gpuEnd(cpu0);

            // ── вернуть состояние ──
            finish(prevRead, prevDraw, w, h, 4);
        } catch (Throwable t) {
            failed = true;
            ModuleManager.LOG.warn("Mraz1 PostFX disabled", t);
        }
    }

    /**
     * Вернуть игре согласованное состояние без запросов glGet (они заставляют драйвер ждать):
     * текстуры — через кэш GlStateManager, программа — 0 и сброс «последней программы» у игры, VAO — 0.
     */
    private static void finish(int prevRead, int prevDraw, int w, int h, int units) {
        for (int i = units - 1; i >= 0; i--) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
        }
        GL20.glUseProgram(0);
        GlProgramCache.forget();
        GL30.glBindVertexArray(0);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GlStateManager._viewport(0, 0, w, h);
    }

    // ── профилирование стенда: время GPU (timer query, без ожидания) и CPU, среднее за 5 с в лог ──
    public static boolean profile = Boolean.getBoolean("mraz1.pfx.profile");
    private static final int[] QUERY = new int[2];
    private static final boolean[] QUSED = new boolean[2];
    private static int qi, samples;
    private static double gpuSum, cpuSum;
    private static long reportAt;

    private static void gpuBegin() {
        if (QUERY[0] == 0) {
            QUERY[0] = GL33.glGenQueries();
            QUERY[1] = GL33.glGenQueries();
        }
        GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, QUERY[qi]);
    }

    private static void gpuEnd(long cpu0) {
        GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
        QUSED[qi] = true;
        qi ^= 1;
        // результат прошлого кадра — если уже готов
        if (QUSED[qi] && GL33.glGetQueryObjecti(QUERY[qi], GL33.GL_QUERY_RESULT_AVAILABLE) != 0) {
            gpuSum += GL33.glGetQueryObjecti64(QUERY[qi], GL33.GL_QUERY_RESULT) / 1e6;
            samples++;
        }
        cpuSum += (System.nanoTime() - cpu0) / 1e6;
        long now = System.currentTimeMillis();
        if (now > reportAt && samples > 0) {
            ModuleManager.LOG.info("PostFX profile: GPU {} ms, CPU {} ms (avg of {})", String.format("%.3f", gpuSum / samples), String.format("%.3f", cpuSum / samples), samples);
            gpuSum = cpuSum = 0;
            samples = 0;
            reportAt = now + 5000;
        }
    }

    private static void bindUnit(int unit, int tex) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(tex);
    }

    private static void pass(int fbo, int w, int h) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, w, h);
        GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    // ── ресурсы ──────────────────────────────────────────────

    private static void init() {
        vao = GlStateManager._glGenVertexArrays();
        srcFbo = GlStateManager.glGenFramebuffers();
        String vsh = read("fullscreen.vsh");
        progDown = program(vsh, read("kawase_down.fsh"));
        progUp = program(vsh, read("kawase_up.fsh"));
        progComp = program(vsh, read("composite.fsh"));
        progDepth = program(vsh, DEPTH_COPY);
        pdDepth = loc(progDepth, "uDepth");
        pdReverse = loc(progDepth, "uReverse");
        dSrc = loc(progDown, "uSrc");
        dTexel = loc(progDown, "uTexel");
        dThreshold = loc(progDown, "uThreshold");
        dKnee = loc(progDown, "uKnee");
        uSrc = loc(progUp, "uSrc");
        uSame = loc(progUp, "uSame");
        uTexel = loc(progUp, "uTexel");
        uRadius = loc(progUp, "uRadius");
        uSameWeight = loc(progUp, "uSameWeight");
        int p = progComp;
        cScene = loc(p, "uScene");
        cBloom = loc(p, "uBloom");
        cDepth = loc(p, "uDepth");
        cBlur = loc(p, "uBlur");
        cRes = loc(p, "uRes");
        cTime = loc(p, "uTime");
        cNearFar = loc(p, "uNearFar");
        cExposure = loc(p, "uExposure");
        cTonemap = loc(p, "uTonemap");
        cBloomS = loc(p, "uBloomStrength");
        cLift = loc(p, "uLift");
        cGamma = loc(p, "uGamma");
        cGain = loc(p, "uGain");
        cSat = loc(p, "uSat");
        cContrast = loc(p, "uContrast");
        cVignette = loc(p, "uVignette");
        cGrain = loc(p, "uGrain");
        cCA = loc(p, "uCA");
        cAO = loc(p, "uAO");
        cAOSamples = loc(p, "uAOSamples");
        cFog = loc(p, "uFog");
        cFogColor = loc(p, "uFogColor");
        cSun = loc(p, "uSun");
        cRays = loc(p, "uRays");
        cRaySamples = loc(p, "uRaySamples");
        cDof = loc(p, "uDof");
        cDofRange = loc(p, "uDofRange");
        cWater = loc(p, "uWater");
        cSkyKeep = loc(p, "uSkyKeep");
    }

    private static int loc(int program, String name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }

    private static String read(String name) {
        try (InputStream in = PostFx.class.getResourceAsStream("/assets/mraz1visual/postfx/" + name)) {
            if (in == null) throw new IllegalStateException("no shader " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int program(String vsh, String fsh) {
        int v = shader(GL20.GL_VERTEX_SHADER, vsh), f = shader(GL20.GL_FRAGMENT_SHADER, fsh);
        int p = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(p, v);
        GlStateManager.glAttachShader(p, f);
        GlStateManager.glLinkProgram(p);
        GlStateManager.glDeleteShader(v);
        GlStateManager.glDeleteShader(f);
        if (GlStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("link: " + GlStateManager.glGetProgramInfoLog(p, 4096));
        return p;
    }

    private static int shader(int type, String src) {
        int s = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(s, src);
        GlStateManager.glCompileShader(s);
        if (GlStateManager.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException("compile: " + GlStateManager.glGetShaderInfoLog(s, 4096));
        return s;
    }

    private static void ensure(int w, int h) {
        if (w == baseW && h == baseH && sceneTex != 0) return;
        release();
        baseW = w;
        baseH = h;
        int[] t = texture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
        sceneTex = t[0];
        sceneFbo = t[1];
        t = texture(w, h, GL30.GL_R32F, GL11.GL_FLOAT);
        depthTex = t[0];
        depthFbo = t[1];
        for (int i = 0; i < MAX_LEVELS; i++) {
            lw[i] = Math.max(1, w >> (i + 1));
            lh[i] = Math.max(1, h >> (i + 1));
            t = texture(lw[i], lh[i], GL30.GL_R11F_G11F_B10F, GL11.GL_FLOAT);
            downTex[i] = t[0];
            downFbo[i] = t[1];
            t = texture(lw[i], lh[i], GL30.GL_R11F_G11F_B10F, GL11.GL_FLOAT);
            upTex[i] = t[0];
            upFbo[i] = t[1];
        }
        for (int i = 0; i < 2; i++) {
            t = texture(lw[i], lh[i], GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            blurTex[i] = t[0];
            blurFbo[i] = t[1];
        }
        srcTexId = -1;
    }

    /** Текстура с линейной фильтрацией и её FBO. @return {текстура, fbo} */
    private static int[] texture(int w, int h, int internal, int type) {
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int tex = GlStateManager._genTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, GL11.GL_RGBA, type, null);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        GL13.glActiveTexture(prevActive);
        int prevFb = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFb);
        return new int[]{tex, fbo};
    }

    private static void release() {
        if (sceneTex != 0) {
            GlStateManager._deleteTexture(sceneTex);
            GlStateManager._glDeleteFramebuffers(sceneFbo);
        }
        sceneTex = sceneFbo = 0;
        if (depthTex != 0) {
            GlStateManager._deleteTexture(depthTex);
            GlStateManager._glDeleteFramebuffers(depthFbo);
        }
        depthTex = depthFbo = 0;
        for (int i = 0; i < MAX_LEVELS; i++) {
            if (downTex[i] != 0) {
                GlStateManager._deleteTexture(downTex[i]);
                GlStateManager._glDeleteFramebuffers(downFbo[i]);
                GlStateManager._deleteTexture(upTex[i]);
                GlStateManager._glDeleteFramebuffers(upFbo[i]);
            }
            downTex[i] = downFbo[i] = upTex[i] = upFbo[i] = 0;
        }
        for (int i = 0; i < 2; i++) {
            if (blurTex[i] != 0) {
                GlStateManager._deleteTexture(blurTex[i]);
                GlStateManager._glDeleteFramebuffers(blurFbo[i]);
            }
            blurTex[i] = blurFbo[i] = 0;
        }
    }
}
