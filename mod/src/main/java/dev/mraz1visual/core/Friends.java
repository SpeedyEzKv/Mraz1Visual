package dev.mraz1visual.core;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Список друзей: config/mraz1visual/friends.txt, один ник на строку. Перечитывается при изменении файла. */
public final class Friends {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("friends.txt");
    private static final Set<String> names = new LinkedHashSet<>();
    private static long stamp = -1;

    private Friends() {}

    public static boolean is(String name) {
        reloadIfChanged();
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    public static List<String> all() {
        reloadIfChanged();
        return new ArrayList<>(names);
    }

    /** Добавить или убрать. Возвращает true, если ник теперь в друзьях. */
    public static boolean toggle(String name) {
        reloadIfChanged();
        String n = name.toLowerCase(Locale.ROOT);
        boolean now = !names.remove(n);
        if (now) names.add(n);
        save();
        return now;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder sb = new StringBuilder("# Друзья: один ник на строку\n");
            for (String n : names) sb.append(n).append('\n');
            Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8);
            stamp = Files.getLastModifiedTime(FILE).toMillis();
        } catch (Exception ignored) {
        }
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
