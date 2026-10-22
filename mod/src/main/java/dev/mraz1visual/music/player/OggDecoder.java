package dev.mraz1visual.music.player;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** OGG Vorbis через stb_vorbis (уже есть в LWJGL игры): файл в нативной памяти, декод потоком. */
final class OggDecoder implements Decoder {
    private final ByteBuffer data;
    private final long handle;
    private final int rate, channels;
    private final long total;
    private ShortBuffer out = MemoryUtil.memAllocShort(8192);

    OggDecoder(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes).flip();
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer err = st.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, err, null);
            if (handle == 0) {
                MemoryUtil.memFree(data);
                throw new IOException("ogg: ошибка " + err.get(0));
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(st);
            STBVorbis.stb_vorbis_get_info(handle, info);
            rate = info.sample_rate();
            channels = info.channels();
        }
        total = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
    }

    @Override public int sampleRate() { return rate; }

    @Override public int channels() { return channels; }

    @Override
    public int read(short[] buf) {
        if (out.capacity() < buf.length) {
            MemoryUtil.memFree(out);
            out = MemoryUtil.memAllocShort(buf.length);
        }
        out.clear().limit(buf.length - buf.length % channels);
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, out);
        if (frames <= 0) return -1;
        int n = frames * channels;
        out.get(0, buf, 0, n);
        return n;
    }

    @Override public long lengthMs() { return total * 1000 / Math.max(1, rate); }

    @Override public void seek(long ms) { STBVorbis.stb_vorbis_seek(handle, (int) (ms * rate / 1000)); }

    @Override
    public void close() {
        STBVorbis.stb_vorbis_close(handle);
        MemoryUtil.memFree(data);
        MemoryUtil.memFree(out);
    }
}
