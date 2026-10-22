package dev.mraz1visual.music.player;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** MP3 через JLayer (LGPL-2.1): кадр за кадром; длина — по первому кадру и размеру файла. */
final class Mp3Decoder implements Decoder {
    private final Path path;
    private Bitstream bits;
    private javazoom.jl.decoder.Decoder dec;
    private int rate = 44100, channels = 2;
    private final long length;
    private short[] pending = new short[0];
    private int pendingPos, pendingLen;

    Mp3Decoder(Path p) throws IOException {
        path = p;
        open();
        long len = 0;
        try {
            Header h = bits.readFrame();
            if (h != null) {
                rate = h.frequency();
                channels = h.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                len = (long) h.total_ms((int) Files.size(p));
                bits.unreadFrame();
            }
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
        length = len;
    }

    private void open() throws IOException {
        bits = new Bitstream(new BufferedInputStream(Files.newInputStream(path), 64 * 1024));
        dec = new javazoom.jl.decoder.Decoder();
    }

    @Override public int sampleRate() { return rate; }

    @Override public int channels() { return channels; }

    @Override
    public int read(short[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            if (pendingPos >= pendingLen && !next()) break;
            int k = Math.min(buf.length - n, pendingLen - pendingPos);
            System.arraycopy(pending, pendingPos, buf, n, k);
            pendingPos += k;
            n += k;
        }
        return n == 0 ? -1 : n;
    }

    /** Следующий кадр в pending. */
    private boolean next() throws IOException {
        try {
            Header h = bits.readFrame();
            if (h == null) return false;
            SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bits);
            bits.closeFrame();
            int len = sb.getBufferLength();
            if (pending.length < len) pending = new short[len];
            System.arraycopy(sb.getBuffer(), 0, pending, 0, len);
            pendingLen = len;
            pendingPos = 0;
            channels = sb.getChannelCount();
            return true;
        } catch (Exception e) {
            // битый кадр в конце файла — просто конец
            return false;
        }
    }

    @Override public long lengthMs() { return length; }

    @Override
    public void seek(long ms) throws IOException {
        close();
        open();
        pendingLen = pendingPos = 0;
        // пропускаем кадры без декодирования
        try {
            float t = 0;
            while (t < ms) {
                Header h = bits.readFrame();
                if (h == null) break;
                t += h.ms_per_frame();
                bits.closeFrame();
            }
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            bits.close();
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
    }
}
