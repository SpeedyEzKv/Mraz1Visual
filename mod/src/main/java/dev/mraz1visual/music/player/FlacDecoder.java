package dev.mraz1visual.music.player;

import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** FLAC через jflac (Apache-2.0): 16 и 24 бита, кадр за кадром. */
final class FlacDecoder implements Decoder {
    private final Path path;
    private InputStream in;
    private FLACDecoder dec;
    private StreamInfo info;
    private ByteData bd = new ByteData(0);
    private short[] pending = new short[0];
    private int pendingPos, pendingLen;

    FlacDecoder(Path p) throws IOException {
        path = p;
        open();
    }

    private void open() throws IOException {
        in = new BufferedInputStream(Files.newInputStream(path), 64 * 1024);
        dec = new FLACDecoder(in);
        dec.readMetadata();
        info = dec.getStreamInfo();
        if (info == null) throw new IOException("flac без STREAMINFO");
    }

    @Override public int sampleRate() { return info.getSampleRate(); }

    @Override public int channels() { return info.getChannels(); }

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

    private boolean next() throws IOException {
        Frame f = dec.readNextFrame();
        if (f == null) return false;
        bd = dec.decodeFrame(f, bd);
        byte[] b = bd.getData();
        int bps = info.getBitsPerSample(), bytesPer = (bps + 7) / 8;
        int samples = bd.getLen() / bytesPer;
        if (pending.length < samples) pending = new short[samples];
        for (int i = 0; i < samples; i++) {
            int o = i * bytesPer;
            pending[i] = switch (bytesPer) {
                case 1 -> (short) ((b[o] & 255) - 128 << 8);
                case 2 -> (short) ((b[o] & 255) | b[o + 1] << 8);
                default -> (short) ((b[o + bytesPer - 2] & 255) | b[o + bytesPer - 1] << 8); // 24/32 бита — старшие 16
            };
        }
        pendingLen = samples;
        pendingPos = 0;
        return true;
    }

    @Override public long lengthMs() { return info.getTotalSamples() * 1000 / Math.max(1, info.getSampleRate()); }

    @Override
    public void seek(long ms) throws IOException {
        dec.seek(ms * info.getSampleRate() / 1000);
        pendingLen = pendingPos = 0;
    }

    @Override public void close() throws IOException { in.close(); }
}
