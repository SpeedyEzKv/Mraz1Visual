package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.menu.WallpaperScreen;
import dev.mraz1visual.wallpaper.WallpaperPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Своё небо: обои из Wallpaper Engine (видео, анимации, картинки) вместо ванильного неба.
 *
 * Небо рисуется в самом начале прохода мира — до блоков и без записи глубины, поэтому всё,
 * что есть в мире (блоки, мобы, вода, облака), рисуется поверх, а обои видны только там, где небо.
 * Кадр — та же текстура, что и на фоне меню (ffmpeg, до 1440p/2160p, линейная фильтрация).
 */
public final class CustomSky extends Module {
    private static CustomSky instance;
    private static final float DIST = 8f;
    private static final int PANO_SEGMENTS = 48;

    final EnumSetting mode = add(new EnumSetting("mode", "Вид", 0,
            "backdrop", "Фон за миром", "panorama", "Панорама вокруг"));
    final SliderSetting parallax = add(new SliderSetting("parallax", "Параллакс (фон)", 40, 0, 100, 5, " %"));
    final SliderSetting repeats = add(new SliderSetting("repeats", "Повторов по кругу (панорама)", 2, 1, 6, 1, ""));
    final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость", 100, 20, 100, 5, " %"));
    final BoolSetting overworldOnly = add(new BoolSetting("overworld_only", "Только в обычном мире", true));
    final ActionSetting pick = add(new ActionSetting("pick", "Обои (общие с меню)", "Выбрать ›",
            () -> Minecraft.getInstance().setScreen(new WallpaperScreen(Minecraft.getInstance().screen))));

    private static ByteBufferBuilder buffer;
    private static MultiBufferSource.BufferSource source;

    public CustomSky() {
        super("custom_sky", "Custom Sky", "Обои из Wallpaper Engine вместо неба — видео и картинки в хорошем качестве.", Category.VISUALS);
        instance = this;
    }

    public static void register() {
        WorldRenderEvents.START_MAIN.register(ctx -> {
            CustomSky s = instance;
            if (s == null || !s.isEnabled()) return;
            try {
                Quaternionf rot = ctx.worldState().cameraRenderState.orientation;
                if (rot != null) s.render(rot);
            } catch (Throwable t) {
                ModuleManager.LOG.error("Custom sky failed, disabling", t);
                s.setEnabled(false);
            }
        });
    }

    private void render(Quaternionf rot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (overworldOnly.get() && mc.level.dimension() != Level.OVERWORLD) return;
        WallpaperPlayer.SkyFrame frame = WallpaperPlayer.skyFrame();
        if (frame == null) return;

        if (buffer == null) {
            buffer = new ByteBufferBuilder(1 << 16);
            source = MultiBufferSource.immediate(buffer);
        }
        int b = Math.round(brightness.getF() / 100f * 255f);
        int color = 0xFF000000 | b << 16 | b << 8 | b;
        // textSeeThrough: без проверки и записи глубины, без освещения — то, что нужно для неба
        VertexConsumer vc = source.getBuffer(RenderTypes.textSeeThrough(frame.id()));
        Matrix4f pose = new Matrix4f();
        if (mode.getId().equals("panorama")) panorama(vc, pose, frame, color);
        else backdrop(vc, pose, rot, frame, color, mc);
        source.endBatch();
    }

    /** Картинка во весь экран за миром, чуть больше экрана — сдвигается при повороте головы. */
    private void backdrop(VertexConsumer vc, Matrix4f pose, Quaternionf rot, WallpaperPlayer.SkyFrame f, int color, Minecraft mc) {
        var win = mc.getWindow();
        float aspect = win.getWidth() / (float) Math.max(1, win.getHeight());
        float fov = (float) Math.toRadians(Math.max(30, mc.options.fov().get()) + 20);
        float hh = (float) (DIST * Math.tan(fov / 2)) * 1.25f, hw = hh * aspect;

        // кусок картинки с пропорциями экрана (обрезка как у фона меню)
        float zoom = 1f + parallax.getF() / 100f * 0.35f;
        float sw = Math.min(1f, (f.h() * aspect) / f.w()) / zoom, sh = Math.min(1f, f.w() / (aspect * f.h())) / zoom;
        float yaw = (float) Math.toRadians(mc.player.getYRot()), pitch = mc.player.getXRot() / 90f;
        float mu = (1f - sw) / 2f, mv = (1f - sh) / 2f;
        float cu = 0.5f + mu * (float) Math.sin(yaw), cv = 0.5f + mv * pitch * 0.8f;
        float u0 = cu - sw / 2, u1 = cu + sw / 2, v0 = cv - sh / 2, v1 = cv + sh / 2;

        Vector3f tl = rot.transform(new Vector3f(-hw, hh, -DIST)), tr = rot.transform(new Vector3f(hw, hh, -DIST));
        Vector3f br = rot.transform(new Vector3f(hw, -hh, -DIST)), bl = rot.transform(new Vector3f(-hw, -hh, -DIST));
        v(vc, pose, bl, u0, v1, color);
        v(vc, pose, br, u1, v1, color);
        v(vc, pose, tr, u1, v0, color);
        v(vc, pose, tl, u0, v0, color);
    }

    /** Цилиндр вокруг игрока: картинка повторяется по кругу, сверху и снизу — крышки в цвет краёв. */
    private void panorama(VertexConsumer vc, Matrix4f pose, WallpaperPlayer.SkyFrame f, int color) {
        int rep = Math.max(1, repeats.getI());
        float circ = (float) (2 * Math.PI * DIST);
        // высота полосы по пропорциям одного повтора
        float h = circ / rep * f.h() / (float) f.w();
        float y0 = -h * 0.3f, y1 = h * 0.7f;
        int n = PANO_SEGMENTS;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float x0 = (float) Math.sin(a0) * DIST, z0 = (float) -Math.cos(a0) * DIST;
            float x1 = (float) Math.sin(a1) * DIST, z1 = (float) -Math.cos(a1) * DIST;
            float u0 = (float) i / n * rep, u1 = (float) (i + 1) / n * rep;
            // зеркалим каждый второй повтор, чтобы не было шва на стыках
            float uu0 = mirror(u0), uu1 = mirror(u1);
            if (Math.floor(u0) != Math.floor(u1 - 1e-4f)) { uu0 = mirror(u0 + 1e-4f); uu1 = mirror(u1 - 1e-4f); }
            v(vc, pose, x0, y0, z0, uu0, 1f, color);
            v(vc, pose, x1, y0, z1, uu1, 1f, color);
            v(vc, pose, x1, y1, z1, uu1, 0f, color);
            v(vc, pose, x0, y1, z0, uu0, 0f, color);
            // крышки: тянем крайние строки картинки к зениту и надиру
            v(vc, pose, x0, y1, z0, uu0, 0f, color);
            v(vc, pose, x1, y1, z1, uu1, 0f, color);
            v(vc, pose, 0, DIST * 4, 0, (uu0 + uu1) / 2, 0f, color);
            v(vc, pose, 0, DIST * 4, 0, (uu0 + uu1) / 2, 0f, color);
            v(vc, pose, 0, -DIST * 4, 0, (uu0 + uu1) / 2, 1f, color);
            v(vc, pose, 0, -DIST * 4, 0, (uu0 + uu1) / 2, 1f, color);
            v(vc, pose, x1, y0, z1, uu1, 1f, color);
            v(vc, pose, x0, y0, z0, uu0, 1f, color);
        }
    }

    private static float mirror(float u) {
        int k = (int) Math.floor(u);
        float fr = u - k;
        return (k & 1) == 0 ? fr : 1f - fr;
    }

    private static void v(VertexConsumer vc, Matrix4f pose, Vector3f p, float u, float v, int color) {
        v(vc, pose, p.x, p.y, p.z, u, v, color);
    }

    private static void v(VertexConsumer vc, Matrix4f pose, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setLight(0xF000F0);
    }
}
