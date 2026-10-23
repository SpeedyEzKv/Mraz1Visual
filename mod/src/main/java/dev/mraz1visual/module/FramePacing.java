package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;

/**
 * Ограничение кадров: в меню ванилла держит 60 FPS — здесь выбирается своё значение,
 * а в игре можно включить ровный предел под монитор (время кадра не скачет 1000 ↔ 200).
 *
 * Точный ограничитель заменяет ванильный: тот ждёт через glfwWaitEventsTimeout, а на Windows его
 * точность ~15 мс — предел 144 на деле давал ~60 FPS, и время кадра «плыло». Здесь — короткий сон
 * по 1 мс и точная доводка до срока, срок считается по расписанию (без накопления ошибки).
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
    final BoolSetting precise = add(new BoolSetting("precise", "Точный ограничитель (ровное время кадра)", true));

    private static int monitorHz = 60;
    private static long hzCheckedAt;

    // точный ограничитель: срок прошлого кадра и оценка «пересыпа» Thread.sleep(1)
    private static long lastFrame;
    private static long sleepSlack = 1_500_000L;

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
                int hz = dev.mraz1visual.compat.Platform.monitorHz();
                if (hz > 0) monitorHz = hz;
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

    /**
     * Из миксина FramerateLimitTracker.getFramerateLimit: ванильный предел → наш.
     * Свёрнутое окно и AFK не трогаем — там ванилла сознательно экономит (10–30 FPS).
     */
    public static int limit(int vanilla, FramerateLimitTracker.FramerateThrottleReason reason) {
        FramePacing f = instance;
        if (f == null) return vanilla;
        if (reason != FramerateLimitTracker.FramerateThrottleReason.NONE
                && reason != FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU) return vanilla;
        Minecraft mc = Minecraft.getInstance();
        int user = mc.options.framerateLimit().get();
        if (mc.screen != null) return Math.min(UNLIMITED, choice(f.menuFps.getId(), user));
        if (f.gameFps.getId().equals("off")) return vanilla;
        int cap = choice(f.gameFps.getId(), user);
        return user < UNLIMITED ? Math.min(user, cap) : Math.min(UNLIMITED, cap);
    }

    /**
     * Из миксина RenderSystem.limitDisplayFPS. true — кадр выдержан здесь, ванильное ожидание не нужно.
     * События окна всё равно опрашиваются в flipFrame каждый кадр.
     */
    public static boolean pace(int fps) {
        FramePacing f = instance;
        if (f == null || !f.precise.get() || fps <= 0) return false;
        long period = 1_000_000_000L / fps;
        long now = System.nanoTime();
        long target = lastFrame + period;
        if (now >= target) {
            // опоздали чуть-чуть — держим расписание; сильно (тяжёлый кадр, смена предела) — начинаем заново
            lastFrame = now - target > period ? now : target;
            return true;
        }
        while (true) {
            long rem = target - System.nanoTime();
            if (rem <= 0) break;
            if (rem > sleepSlack) {
                long t0 = System.nanoTime();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // сколько сон реально занял сверх 1 мс — плавная оценка, от 1 до 4 мс
                long over = System.nanoTime() - t0 - 1_000_000L;
                sleepSlack = Math.max(1_000_000L, Math.min(4_000_000L, (sleepSlack * 7 + (over + 1_000_000L)) / 8));
            } else {
                Thread.onSpinWait();
            }
        }
        lastFrame = target;
        return true;
    }
}
