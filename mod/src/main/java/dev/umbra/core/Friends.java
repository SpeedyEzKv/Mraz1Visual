package dev.umbra.core;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Список друзей: config/umbra/friends.txt, один ник на строку. Перечитывается при изменении файла. */
public final class Friends {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("umbra").resolve("friends.txt");
    private static final Set<String> names = new HashSet<>();
    private static long stamp = -1;

    private Friends() {}

    public static boolean is(String name) {
        reloadIfChanged();
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    private static void reloadIfChanged() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, "# Друзья: один ник на строку\n", StandardCharsets.UTF_8);
            }
            long m = Files.getLastModifiedTime(FILE).toMillis();
            if (m == stamp) return;
            stamp = m;
            names.clear();
            for (String l : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                l = l.trim();
                if (!l.isEmpty() && !l.startsWith("#")) names.add(l.toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        }
    }
}
