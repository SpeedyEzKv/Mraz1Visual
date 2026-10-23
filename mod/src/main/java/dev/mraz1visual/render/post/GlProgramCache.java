package dev.mraz1visual.render.post;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mraz1visual.core.ModuleManager;

import java.lang.reflect.Field;

/**
 * Игра помнит последнюю программу шейдера (GlCommandEncoder.lastProgram) и не переключает её, если та же.
 * После своих проходов сбрасываем эту память — следующий рисунок игры сам выставит свою программу.
 * Кодировщик ищется один раз: в 1.21.11 его отдаёт устройство напрямую, в 26.x — через обёртку над бэкендом.
 */
final class GlProgramCache {
    private GlProgramCache() {}

    private static Object encoder;
    private static Field last;
    private static boolean missing;

    static void forget() {
        if (missing) return;
        try {
            if (last == null) resolve();
            last.set(encoder, null);
        } catch (Throwable t) {
            missing = true;
            ModuleManager.LOG.warn("PostFX: could not reset program cache", t);
        }
    }

    private static void resolve() throws ReflectiveOperationException {
        Object enc = RenderSystem.getDevice().createCommandEncoder();
        Field f = find(enc.getClass());
        if (f == null) {
            // 26.x: CommandEncoder → поле-бэкенд (GlCommandEncoder)
            for (Field c : enc.getClass().getDeclaredFields()) {
                c.setAccessible(true);
                Object v = c.get(enc);
                if (v != null && (f = find(v.getClass())) != null) {
                    enc = v;
                    break;
                }
            }
        }
        if (f == null) throw new NoSuchFieldException("lastProgram");
        encoder = enc;
        last = f;
    }

    private static Field find(Class<?> c) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals("lastProgram")) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }
}
