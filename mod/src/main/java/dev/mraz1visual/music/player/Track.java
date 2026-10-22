package dev.mraz1visual.music.player;

import java.nio.file.Path;

/** Трек библиотеки: файл и теги (длина — когда трек впервые открыт). */
public final class Track {
    public final Path path;
    public final String title, artist, album;
    public volatile long lengthMs;
    /** Для поиска — всё в нижнем регистре одной строкой. */
    final String key;

    public Track(Path path, String title, String artist, String album) {
        this.path = path;
        this.title = title;
        this.artist = artist.isBlank() ? "Неизвестный исполнитель" : artist;
        this.album = album.isBlank() ? "Без альбома" : album;
        this.key = (title + " " + artist + " " + album).toLowerCase(java.util.Locale.ROOT);
    }

    public boolean matches(String q) {
        return q.isEmpty() || key.contains(q);
    }
}
