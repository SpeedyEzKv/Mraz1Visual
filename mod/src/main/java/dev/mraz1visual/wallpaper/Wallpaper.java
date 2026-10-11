package dev.mraz1visual.wallpaper;

import java.nio.file.Path;

/**
 * Одни обои из Wallpaper Engine (или свой файл).
 *
 * @param media   что проигрывать: видео, gif или картинка
 * @param preview картинка для плитки в списке (может совпадать с media)
 * @param kind    VIDEO — настоящее видео обоев; PREVIEW — сцена/веб, играем их анимированное превью; IMAGE — картинка
 */
public record Wallpaper(String key, String title, Kind kind, String source, Path media, Path preview, String weType) {
    public enum Kind { VIDEO, PREVIEW, IMAGE }

    public String label() {
        return switch (kind) {
            case VIDEO -> "Видео";
            case PREVIEW -> typeName() + (isAnimated(media) ? " · анимированное превью" : " · превью-картинка");
            case IMAGE -> isAnimated(media) ? "Анимация" : "Картинка";
        };
    }

    private String typeName() {
        return switch (weType == null ? "" : weType) {
            case "web" -> "Веб";
            case "application" -> "Программа";
            default -> "Сцена";
        };
    }

    public static boolean isAnimated(Path p) {
        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return !(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".bmp"));
    }
}
