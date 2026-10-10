package dev.mraz1visual.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Единственное место, где мод опрашивает клавиатуру.
 * Если Mojang поменяет API ввода — правится только этот файл.
 */
public final class Keys {
    private Keys() {}

    public static boolean isDown(int key) {
        if (key < 0) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static String name(int key) {
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        } catch (Throwable t) {
            return "#" + key;
        }
    }

    /** Клавиши, которые сбрасывают бинд при назначении. */
    public static boolean isUnbindKey(int key) {
        return key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
    }
}
