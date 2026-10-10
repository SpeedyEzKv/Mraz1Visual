package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Приближение по удержанию клавиши, как подзорная труба. */
public final class Zoom extends Module {
    private static Zoom instance;

    final KeySetting key = add(new KeySetting("key", "Клавиша (держать)", GLFW.GLFW_KEY_C));
    final SliderSetting factor = add(new SliderSetting("factor", "Приближение", 4, 1.5, 10, 0.5, "×"));
    final BoolSetting smoothCam = add(new BoolSetting("smooth", "Плавная камера при зуме", true));

    private boolean held;
    private float current = 1f;
    private long last;
    private boolean smoothWas;

    public Zoom() {
        super("zoom", "Zoom", "Приближение по удержанию клавиши.", Category.VISUALS);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean now = mc.screen == null && Keys.isDown(key.get());
        if (now != held) {
            held = now;
            if (smoothCam.get()) {
                if (now) {
                    smoothWas = mc.options.smoothCamera;
                    mc.options.smoothCamera = true;
                } else {
                    mc.options.smoothCamera = smoothWas;
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        if (held && smoothCam.get()) Minecraft.getInstance().options.smoothCamera = smoothWas;
        held = false;
        current = 1f;
    }

    /** Вызывается миксином: делим поле зрения. */
    public static float apply(float fov) {
        Zoom z = instance;
        if (z == null || !z.isEnabled()) return fov;
        long now = System.nanoTime();
        float dt = z.last == 0 ? 0 : Math.min(0.1f, (now - z.last) / 1e9f);
        z.last = now;
        float target = z.held ? z.factor.getF() : 1f;
        z.current += (target - z.current) * (1f - (float) Math.exp(-14f * dt));
        return fov / z.current;
    }
}
