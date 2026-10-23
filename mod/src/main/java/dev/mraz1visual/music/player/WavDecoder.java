package dev.mraz1visual.music.player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** WAV и прочее, что понимает javax.sound: приводится к PCM 16 бит little-endian. */
final class WavDecoder implements Decoder {
    private final Path path;
    private AudioInputStream in;
    private final AudioFormat fmt;
    private final long frames;
    private byte[] bytes = new byte[0];

    WavDecoder(Path p) throws IOException {
        path = p;
        open();
        fmt = in.getFormat();
        frames = in.getFrameLength();
    }

    private void open() throws IOException {
        try {
            AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
            AudioFormat f = src.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
            in = AudioSystem.getAudioInputStream(pcm, src);
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    @Override public int sampleRate() { return Math.round(fmt.getSampleRate()); }

    @Override public int channels() { return fmt.getChannels(); }

    @Override
    public int read(short[] buf) throws IOException {
        if (bytes.length < buf.length * 2) bytes = new byte[buf.length * 2];
        int n = in.read(bytes, 0, buf.length * 2);
        if (n <= 0) return -1;
        int s = n / 2;
        for (int i = 0; i < s; i++) buf[i] = (short) ((bytes[i * 2] & 255) | bytes[i * 2 + 1] << 8);
        return s;
    }

    @Override public long lengthMs() { return frames > 0 ? (long) (frames * 1000 / fmt.getFrameRate()) : 0; }

    @Override
    public void seek(long ms) throws IOException {
        in.close();
        open();
        long skip = (long) (ms / 1000.0 * fmt.getSampleRate()) * fmt.getChannels() * 2;
        while (skip > 0) {
            long k = in.skip(skip);
            if (k <= 0) break;
            skip -= k;
        }
    }

    @Override public void close() throws IOException { in.close(); }
}
