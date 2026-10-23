package dev.mraz1visual.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Единственное место, где мод опрашивает клавиатуру.
 * Если Mojang поменяет API ввода — правится только этот файл.
 */
public final class Keys {
    private Keys() {}

    public static boolean isDown(int key) {
        if (key < 0) return false;
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(key);
        *///?} else {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        //?}
    }

    public static String name(int key) {
        try {
            //? if >=26.3 {
            /*return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
            *///?} else {
            return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
            //?}
        } catch (Throwable t) {
            return "#" + key;
        }
    }

    /** Клавиши, которые сбрасывают бинд при назначении. */
    public static boolean isUnbindKey(int key) {
        return key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE;
    }
}
