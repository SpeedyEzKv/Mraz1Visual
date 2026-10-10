package dev.mraz1visual.account;

import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Подмена сессии в игре без перезапуска. Единственное место, где мод создаёт User.
 * Ограничение: подпись чата остаётся от сессии, с которой запустили игру, —
 * на серверах со строгой проверкой подписи чата (secure chat) сообщения могут не пройти.
 */
public final class SessionSwap {
    private SessionSwap() {}

    public static void apply(String name, String uuid, String accessToken, String xuid) {
        try {
            User user = new User(name, UUID.fromString(uuid), accessToken,
                    xuid == null || xuid.isEmpty() ? Optional.empty() : Optional.of(xuid), Optional.empty());
            ((MinecraftAccessor) Minecraft.getInstance()).mraz1visual$setUser(user);
            ModuleManager.LOG.info("Session switched to {}", name);
        } catch (Throwable t) {
            ModuleManager.LOG.error("Session switch failed", t);
            AccountStore.status = "Не удалось переключить сессию";
        }
    }
}
