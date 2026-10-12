package dev.mraz1visual.wallpaper;

import java.nio.file.Path;

/**
 * Одни обои из Wallpaper Engine (или свой файл).
 *
 * @param media   VIDEO — видеофайл; WEB — index.html; SCENE/PREVIEW — превью (gif/картинка); IMAGE — сам файл
 * @param preview картинка для плитки в списке (может быть null)
 * @param folder  папка обоев (для сцен — откуда брать scene.pkg), у своих файлов — null
 */
public record Wallpaper(String key, String title, Kind kind, String source, Path media, Path preview, Path folder) {
    public enum Kind { VIDEO, SCENE, WEB, PREVIEW, IMAGE }

    public String label() {
        return switch (kind) {
            case VIDEO -> "Видео";
            case SCENE -> "Сцена";
            case WEB -> "Веб";
            case PREVIEW -> "Программа · превью";
            case IMAGE -> isAnimated(media) ? "Анимация" : "Картинка";
        };
    }

    public static boolean isAnimated(Path p) {
        if (p == null) return false;
        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".mp4") || n.endsWith(".webm");
    }
}
