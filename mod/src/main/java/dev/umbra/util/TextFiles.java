package dev.umbra.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Простые текстовые списки в config/umbra: одна запись на строку, # — комментарий. */
public final class TextFiles {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("umbra");

    private TextFiles() {}

    public static List<String> lines(String file, String template) {
        List<String> out = new ArrayList<>();
        try {
            Path f = DIR.resolve(file);
            if (!Files.exists(f)) {
                Files.createDirectories(DIR);
                Files.writeString(f, template, StandardCharsets.UTF_8);
            }
            for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                l = l.trim();
                if (!l.isEmpty() && !l.startsWith("#")) out.add(l);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
