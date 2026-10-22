package dev.mraz1visual.music.player;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Декодер аудиофайла в 16-битный PCM (интерливинг по каналам). Реализации: WAV (javax.sound), OGG (stb_vorbis из LWJGL),
 * MP3 (JLayer), FLAC (jflac). Работает только в потоке плеера.
 */
public interface Decoder extends Closeable {
    int sampleRate();

    int channels();

    /** Прочитать в buf. @return сколько сэмплов (всех каналов) записано, −1 — конец файла. */
    int read(short[] buf) throws IOException;

    /** Длина в мс (или 0, если неизвестна). */
    long lengthMs();

    /** Перемотка на ms (точность — кадр). */
    void seek(long ms) throws IOException;

    static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".oga") || n.endsWith(".flac") || n.endsWith(".wav");
    }

    static Decoder open(Path p) throws IOException {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3")) return new Mp3Decoder(p);
        if (n.endsWith(".ogg") || n.endsWith(".oga")) return new OggDecoder(p);
        if (n.endsWith(".flac")) return new FlacDecoder(p);
        if (n.endsWith(".wav")) return new WavDecoder(p);
        throw new IOException("формат не поддерживается: " + n);
    }
}
