package dev.mraz1visual.render.post;

import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Автоустановка пака «Mraz1 Shaders» для Iris: при запуске копирует пак из jar в shaderpacks/Mraz1 Shaders
 * (папкой — Iris читает и так). Перезаписывает только при новой версии мода; выбор пака остаётся за игроком.
 */
public final class ShaderPackInstaller {
    private ShaderPackInstaller() {}

    public static final String NAME = "Mraz1 Shaders";

    public static void install() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;
        // синхронно: пак маленький (~20 файлов), а Iris читает shaderpacks сразу после инициализации модов
        run();
    }

    private static void run() {
        try {
            var mod = FabricLoader.getInstance().getModContainer("mraz1visual").orElseThrow();
            String version = mod.getMetadata().getVersion().getFriendlyString();
            Path src = null;
            for (Path root : mod.getRootPaths()) {
                Path p = root.resolve("assets/mraz1visual/shaderpack");
                if (Files.isDirectory(p)) src = p;
            }
            if (src == null) {
                ModuleManager.LOG.warn("Shaderpack not found in mod resources");
                return;
            }
            // отпечаток содержимого: пак обновляется и без смены версии мода (dev-сборки)
            long hash = 17;
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (Files.isRegularFile(p)) hash = hash * 31 + java.util.Arrays.hashCode(Files.readAllBytes(p));
                }
            }
            version += "#" + Long.toHexString(hash);
            Path dst = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(NAME);
            Path stamp = dst.resolve(".mraz1visual-version");
            if (Files.exists(stamp) && Files.readString(stamp, StandardCharsets.UTF_8).trim().equals(version)) return;
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    Path out = dst.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(out);
                    else Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(stamp, version, StandardCharsets.UTF_8);
            ModuleManager.LOG.info("Installed shaderpack {} ({})", NAME, version);
        } catch (Exception e) {
            ModuleManager.LOG.warn("Shaderpack install failed", e);
        }
    }
}
