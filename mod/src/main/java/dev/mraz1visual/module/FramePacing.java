package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Ограничение кадров: в меню ванилла держит 60 FPS — здесь выбирается своё значение,
 * а в игре можно включить ровный предел под монитор (время кадра не скачет 1000 ↔ 200).
 * Всегда включён: это настройка, а не визуал.
 */
public final class FramePacing extends Module {
    private static FramePacing instance;
    /** Значение «без ограничения» в настройках Minecraft. */
    private static final int UNLIMITED = 260;

    final EnumSetting menuFps = add(new EnumSetting("menu_fps", "FPS в меню и инвентаре", 1,
            "game", "Как в игре", "monitor", "Частота монитора", "60", "60", "120", "120", "144", "144", "240", "240"));
    final EnumSetting gameFps = add(new EnumSetting("game_fps", "Ровный FPS в игре", 0,
            "off", "Выкл (как в настройках)", "monitor", "Частота монитора", "monitor2", "2× монитор", "120", "120", "144", "144", "240", "240"));

    private static int monitorHz = 60;
    private static long hzCheckedAt;

    public FramePacing() {
        super("frame_pacing", "Frame Pacing", "FPS в меню выше 60 и ровное время кадра в игре.", Category.PERFORMANCE);
        instance = this;
    }

    @Override
    public boolean alwaysOn() { return true; }

    /** Частота основного монитора, раз в 2 секунды (переключение мониторов). */
    private static int monitorHz() {
        long now = System.currentTimeMillis();
        if (now - hzCheckedAt > 2000) {
            hzCheckedAt = now;
            try {
                long mon = GLFW.glfwGetPrimaryMonitor();
                GLFWVidMode mode = mon == 0 ? null : GLFW.glfwGetVideoMode(mon);
                if (mode != null && mode.refreshRate() > 0) monitorHz = mode.refreshRate();
            } catch (Throwable ignored) {
            }
        }
        return monitorHz;
    }

    private static int choice(String id, int fallback) {
        return switch (id) {
            case "monitor" -> monitorHz();
            case "monitor2" -> monitorHz() * 2;
            case "game" -> fallback;
            default -> {
                try { yield Integer.parseInt(id); } catch (NumberFormatException e) { yield fallback; }
            }
        };
    }

    /** Из миксина FramerateLimitTracker.getFramerateLimit: ванильный предел → наш. */
    public static int limit(int vanilla) {
        FramePacing f = instance;
        if (f == null) return vanilla;
        Minecraft mc = Minecraft.getInstance();
        boolean menu = mc.level == null && mc.screen != null;
        // окно свёрнуто или игрок AFK — ванилла сама снижает FPS ниже 60, это не трогаем
        if (vanilla < 60 && !menu) return vanilla;
        int user = mc.options.framerateLimit().get();
        if (mc.screen != null) return Math.min(UNLIMITED, choice(f.menuFps.getId(), user));
        if (f.gameFps.getId().equals("off")) return vanilla;
        int cap = choice(f.gameFps.getId(), user);
        return user < UNLIMITED ? Math.min(user, cap) : Math.min(UNLIMITED, cap);
    }
}
