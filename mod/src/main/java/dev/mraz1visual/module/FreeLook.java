package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Свободный обзор: пока держишь клавишу, мышь крутит только камеру,
 * а персонаж смотрит туда же, куда смотрел.
 */
public final class FreeLook extends Module {
    private static FreeLook instance;
    final KeySetting key = add(new KeySetting("key", "Клавиша", InputConstants.KEY_LALT));
    final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Вид от третьего лица", true));

    private boolean active;
    private float yaw, pitch;
    private CameraType before;

    public FreeLook() {
        super("free_look", "Free Look", "Покрутить камерой, не поворачивая персонажа.", Category.VISUALS);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean want = p != null && mc.screen == null && Keys.isDown(key.get());
        if (want && !active) {
            yaw = p.getYRot();
            pitch = p.getXRot();
            if (thirdPerson.get()) {
                before = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            active = true;
        } else if (!want && active) {
            stop(mc);
        }
    }

    @Override
    protected void onDisable() {
        if (active) stop(Minecraft.getInstance());
    }

    private void stop(Minecraft mc) {
        active = false;
        if (before != null) mc.options.setCameraType(before);
        before = null;
    }

    public static boolean active() {
        return instance != null && instance.active && instance.isEnabled();
    }

    /** Из миксина Entity.turn: мышь крутит камеру вместо игрока. */
    public static boolean turn(double dy, double dx) {
        if (!active()) return false;
        FreeLook f = instance;
        f.yaw += (float) dy * 0.15f;
        f.pitch = Math.max(-90f, Math.min(90f, f.pitch + (float) dx * 0.15f));
        return true;
    }

    public static float yaw() { return instance.yaw; }

    public static float pitch() { return instance.pitch; }
}
