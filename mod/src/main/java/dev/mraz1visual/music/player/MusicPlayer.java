package dev.mraz1visual.music.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.util.TextFiles;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Встроенный плеер: свой поток вывода (javax.sound, отдельно от звука игры — своя громкость), декод и
 * ресемплинг к 44,1 кГц стерео, 3-полосный эквалайзер с пресетами, кроссфейд между треками, очередь,
 * перемешивание, повтор, плейлисты и продолжение с того же места после перезапуска. Игровой поток только
 * читает готовые поля и ставит команды — никаких фризов.
 */
public final class MusicPlayer {
    public static final int RATE = 44100;
    public static final String[] EQ = {"flat", "Плоский", "bass", "Басы", "vocal", "Вокал", "bright", "Яркий", "soft", "Мягкий"};
    public static final String[] REPEAT = {"off", "Без повтора", "all", "Повтор очереди", "one", "Повтор трека"};
    private static final Path FILE = TextFiles.DIR.resolve("music.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── состояние (читается из игрового потока) ──
    private static final List<Track> queue = Collections.synchronizedList(new ArrayList<>());
    private static volatile int index = -1;
    private static volatile boolean playing;
    private static volatile long positionMs, lengthMs;
    public static volatile float volume = 0.6f;
    public static volatile int eq;
    public static volatile int repeat;
    public static volatile boolean shuffle;
    public static volatile float crossfadeSec = 3f;
    public static final List<Path> folders = Collections.synchronizedList(new ArrayList<>());
    public static final Map<String, List<Path>> playlists = Collections.synchronizedMap(new LinkedHashMap<>());
    /** Меняется при смене трека — для обложки, текста песни и кроссфейда виджета. */
    public static volatile int trackSerial;

    // ── команды ──
    private static volatile int pendingIndex = -1;
    private static volatile long pendingSeek = -1;
    private static volatile boolean stopRequested;
    private static Thread thread;
    private static final Random RND = new Random();

    private MusicPlayer() {}

    public static Track current() {
        int i = index;
        synchronized (queue) {
            return i >= 0 && i < queue.size() ? queue.get(i) : null;
        }
    }

    public static boolean playing() { return playing; }

    public static boolean active() { return current() != null; }

    public static long position() { return positionMs; }

    public static long length() { return lengthMs; }

    public static List<Track> queue() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    // ── команды из GUI и клавиш ──

    /** Поставить список в очередь и играть с позиции start. */
    public static void playList(List<Track> list, int start) {
        synchronized (queue) {
            queue.clear();
            queue.addAll(list);
        }
        pendingIndex = Math.max(0, Math.min(start, list.size() - 1));
        playing = true;
        ensureThread();
    }

    public static void enqueue(Track t) {
        queue.add(t);
        if (current() == null) playList(queue(), queue.size() - 1);
    }

    public static void toggle() {
        if (current() == null) {
            if (!Library.tracks().isEmpty()) playList(Library.tracks(), shuffle ? RND.nextInt(Library.tracks().size()) : 0);
            return;
        }
        if (!playing) resumeIfPaused();
        playing = !playing;
        ensureThread();
    }

    public static void next() {
        int n = nextIndex(true);
        if (n >= 0) { pendingIndex = n; playing = true; ensureThread(); }
    }

    public static void prev() {
        if (positionMs > 3000) { pendingSeek = 0; return; }
        int i = index - 1;
        if (i < 0) i = repeat == 1 ? queue.size() - 1 : 0;
        pendingIndex = i;
        playing = true;
        ensureThread();
    }

    public static void seek(long ms) {
        pendingSeek = Math.max(0, ms);
    }

    public static void stop() {
        stopRequested = true;
        playing = false;
    }

    /** Следующий индекс по режимам; manual — нажата «следующая» (повтор трека не держит). */
    private static int nextIndex(boolean manual) {
        int size = queue.size();
        if (size == 0) return -1;
        if (!manual && repeat == 2) return index;
        if (shuffle && size > 1) {
            int n;
            do n = RND.nextInt(size); while (n == index);
            return n;
        }
        int n = index + 1;
        if (n >= size) return repeat == 1 || manual ? 0 : -1;
        return n;
    }

    private static synchronized void ensureThread() {
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(MusicPlayer::run, "Mraz1Visual player");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    // ── поток вывода ──

    private static final int CHUNK = 2048; // кадров за шаг (~46 мс)

    private static void run() {
        SourceDataLine line = null;
        Source cur = null, nxt = null;
        float[] mix = new float[CHUNK * 2], tmp = new float[CHUNK * 2];
        byte[] out = new byte[CHUNK * 4];
        Eq eqL = new Eq(), eqR = new Eq();
        long framesPlayed = 0;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, RATE / 5 * 4); // ~200 мс буфера
            line.start();
            while (!stopRequested) {
                // команды
                int pi = pendingIndex;
                if (pi >= 0) {
                    pendingIndex = -1;
                    if (cur != null) cur.close();
                    if (nxt != null) { nxt.close(); nxt = null; }
                    cur = openAt(pi, 0);
                    framesPlayed = 0;
                    line.flush();
                }
                long ps = pendingSeek;
                if (ps >= 0 && cur != null) {
                    pendingSeek = -1;
                    cur.seek(ps);
                    framesPlayed = ps * RATE / 1000;
                    line.flush();
                }
                if (!playing || cur == null) {
                    Thread.sleep(25);
                    continue;
                }
                int eqNow = eq;
                eqL.preset(eqNow);
                eqR.preset(eqNow);
                int got = cur.read(mix, CHUNK);
                // кроссфейд: за crossfadeSec до конца подмешиваем начало следующего
                long remaining = lengthMs - framesPlayed * 1000 / RATE;
                float cf = crossfadeSec;
                if (cf > 0.1f && lengthMs > 0 && remaining < cf * 1000 && nxt == null && repeat != 2) {
                    int ni = nextIndex(false);
                    if (ni >= 0 && ni != index) nxt = openSource(ni);
                }
                if (nxt != null) {
                    int g2 = nxt.read(tmp, CHUNK);
                    float k = cf <= 0 ? 1 : Math.max(0, Math.min(1, 1 - remaining / (cf * 1000f)));
                    float a = (float) Math.cos(k * Math.PI / 2), b = (float) Math.sin(k * Math.PI / 2);
                    int n = Math.max(got, g2);
                    for (int i = 0; i < n * 2; i++) {
                        float x = i < got * 2 ? mix[i] : 0, y = i < g2 * 2 ? tmp[i] : 0;
                        mix[i] = x * a + y * b;
                    }
                    got = n;
                }
                if (got <= 0 || (nxt != null && remaining <= 0)) {
                    // трек закончился
                    cur.close();
                    if (nxt != null) {
                        cur = nxt;
                        nxt = null;
                        index = cur.index;
                        lengthMs = cur.lengthMs;
                        framesPlayed = (long) (crossfadeSec * RATE);
                        trackSerial++;
                    } else {
                        int ni = nextIndex(false);
                        if (ni < 0) { playing = false; cur = null; positionMs = 0; continue; }
                        cur = openAt(ni, 0);
                        framesPlayed = 0;
                    }
                    if (got <= 0) continue;
                }
                float vol = volume * volume; // громкость на слух — квадратом
                for (int i = 0; i < got; i++) {
                    float l = eqL.process(mix[i * 2]) * vol, r = eqR.process(mix[i * 2 + 1]) * vol;
                    int li = (int) Math.max(-32768, Math.min(32767, l * 32767)), ri = (int) Math.max(-32768, Math.min(32767, r * 32767));
                    out[i * 4] = (byte) li; out[i * 4 + 1] = (byte) (li >> 8);
                    out[i * 4 + 2] = (byte) ri; out[i * 4 + 3] = (byte) (ri >> 8);
                }
                line.write(out, 0, got * 4);
                framesPlayed += got;
                positionMs = Math.max(0, (framesPlayed * 1000 / RATE) - 180);
            }
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Музыка: поток плеера остановлен", t);
        } finally {
            if (cur != null) cur.close();
            if (nxt != null) nxt.close();
            if (line != null) { line.stop(); line.close(); }
            stopRequested = false;
            playing = false;
        }
    }

    private static Source openAt(int i, long ms) {
        Source s = openSource(i);
        index = i;
        lengthMs = s == null ? 0 : s.lengthMs;
        positionMs = 0;
        trackSerial++;
        if (s != null && ms > 0) s.seek(ms);
        save();
        return s;
    }

    private static Source openSource(int i) {
        Track t;
        synchronized (queue) {
            if (i < 0 || i >= queue.size()) return null;
            t = queue.get(i);
        }
        try {
            Source s = new Source(Decoder.open(t.path), i);
            t.lengthMs = s.lengthMs;
            return s;
        } catch (Exception e) {
            ModuleManager.LOG.warn("Музыка: не открылся {}", t.path, e);
            return null;
        }
    }

    /** Декодер + ресемплинг к 44,1 кГц стерео (float −1..1). */
    private static final class Source {
        final Decoder dec;
        final int index;
        final long lengthMs;
        final double step;
        short[] raw = new short[8192];
        int rawLen, rawPos, ch;
        double frac;
        float pl, pr; // предыдущий кадр для интерполяции
        boolean eof;

        Source(Decoder d, int index) {
            dec = d;
            this.index = index;
            lengthMs = d.lengthMs();
            ch = Math.max(1, d.channels());
            step = d.sampleRate() / (double) RATE;
        }

        /** Следующий исходный кадр в (l, r); false — конец. */
        private boolean nextFrame(float[] lr) {
            if (rawPos + ch > rawLen) {
                try {
                    rawLen = eof ? -1 : dec.read(raw);
                } catch (Exception e) {
                    rawLen = -1;
                }
                rawPos = 0;
                ch = Math.max(1, dec.channels());
                if (rawLen <= 0) { eof = true; return false; }
            }
            lr[0] = raw[rawPos] / 32768f;
            lr[1] = ch > 1 ? raw[rawPos + 1] / 32768f : lr[0];
            rawPos += ch;
            return true;
        }

        private final float[] lr = new float[2];
        private float cl, cr;
        private boolean primed;

        int read(float[] out, int frames) {
            if (!primed) {
                if (!nextFrame(lr)) return -1;
                pl = lr[0]; pr = lr[1];
                if (!nextFrame(lr)) return -1;
                cl = lr[0]; cr = lr[1];
                primed = true;
            }
            int n = 0;
            while (n < frames) {
                while (frac >= 1) {
                    frac -= 1;
                    pl = cl; pr = cr;
                    if (!nextFrame(lr)) return n == 0 ? -1 : n;
                    cl = lr[0]; cr = lr[1];
                }
                float f = (float) frac;
                out[n * 2] = pl + (cl - pl) * f;
                out[n * 2 + 1] = pr + (cr - pr) * f;
                frac += step;
                n++;
            }
            return n;
        }

        void seek(long ms) {
            try {
                dec.seek(ms);
                rawLen = rawPos = 0;
                eof = false;
                primed = false;
            } catch (Exception ignored) {
            }
        }

        void close() {
            try {
                dec.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 3 полосы: низкая полка 120 Гц, середина 1,5 кГц, высокая полка 6 кГц (биквады RBJ). */
    private static final class Eq {
        private final float[][] c = new float[3][5];
        private final float[][] z = new float[3][4];
        private int preset = -1;

        void preset(int p) {
            if (p == preset) return;
            preset = p;
            float[] g = switch (p) {
                case 1 -> new float[]{6, 0, -1};
                case 2 -> new float[]{-2, 4, 1};
                case 3 -> new float[]{-1, 0, 5};
                case 4 -> new float[]{2, -1, -4};
                default -> new float[]{0, 0, 0};
            };
            shelf(c[0], 120, g[0], true);
            peak(c[1], 1500, g[1], 1);
            shelf(c[2], 6000, g[2], false);
        }

        float process(float x) {
            if (preset == 0) return x;
            for (int i = 0; i < 3; i++) {
                float[] k = c[i], s = z[i];
                float y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3];
                s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y;
                x = y;
            }
            return x;
        }

        private static void shelf(float[] k, double f, double gain, boolean low) {
            double A = Math.pow(10, gain / 40), w = 2 * Math.PI * f / RATE, cs = Math.cos(w), sn = Math.sin(w);
            double alpha = sn / 2 * Math.sqrt(2), sq = 2 * Math.sqrt(A) * alpha;
            double b0, b1, b2, a0, a1, a2;
            if (low) {
                b0 = A * ((A + 1) - (A - 1) * cs + sq); b1 = 2 * A * ((A - 1) - (A + 1) * cs); b2 = A * ((A + 1) - (A - 1) * cs - sq);
                a0 = (A + 1) + (A - 1) * cs + sq; a1 = -2 * ((A - 1) + (A + 1) * cs); a2 = (A + 1) + (A - 1) * cs - sq;
            } else {
                b0 = A * ((A + 1) + (A - 1) * cs + sq); b1 = -2 * A * ((A - 1) + (A + 1) * cs); b2 = A * ((A + 1) + (A - 1) * cs - sq);
                a0 = (A + 1) - (A - 1) * cs + sq; a1 = 2 * ((A - 1) - (A + 1) * cs); a2 = (A + 1) - (A - 1) * cs - sq;
            }
            set(k, b0, b1, b2, a0, a1, a2);
        }

        private static void peak(float[] k, double f, double gain, double q) {
            double A = Math.pow(10, gain / 40), w = 2 * Math.PI * f / RATE, alpha = Math.sin(w) / (2 * q), cs = Math.cos(w);
            set(k, 1 + alpha * A, -2 * cs, 1 - alpha * A, 1 + alpha / A, -2 * cs, 1 - alpha / A);
        }

        private static void set(float[] k, double b0, double b1, double b2, double a0, double a1, double a2) {
            k[0] = (float) (b0 / a0); k[1] = (float) (b1 / a0); k[2] = (float) (b2 / a0); k[3] = (float) (a1 / a0); k[4] = (float) (a2 / a0);
        }
    }

    // ── сохранение ──

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("volume", volume);
            o.addProperty("eq", eq);
            o.addProperty("repeat", repeat);
            o.addProperty("shuffle", shuffle);
            o.addProperty("crossfade", crossfadeSec);
            JsonArray f = new JsonArray();
            synchronized (folders) { for (Path p : folders) f.add(p.toString()); }
            o.add("folders", f);
            JsonObject pl = new JsonObject();
            synchronized (playlists) {
                playlists.forEach((name, list) -> {
                    JsonArray a = new JsonArray();
                    for (Path p : list) a.add(p.toString());
                    pl.add(name, a);
                });
            }
            o.add("playlists", pl);
            JsonArray q = new JsonArray();
            synchronized (queue) { for (Track t : queue) q.add(t.path.toString()); }
            o.add("queue", q);
            o.addProperty("index", index);
            o.addProperty("position", positionMs);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ModuleManager.LOG.warn("Музыка: состояние не сохранено", e);
        }
    }

    /** Загрузить настройки и последнюю очередь; resume — сразу встать на сохранённую позицию (на паузе). */
    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            volume = o.has("volume") ? o.get("volume").getAsFloat() : volume;
            eq = o.has("eq") ? o.get("eq").getAsInt() : 0;
            repeat = o.has("repeat") ? o.get("repeat").getAsInt() : 0;
            shuffle = o.has("shuffle") && o.get("shuffle").getAsBoolean();
            crossfadeSec = o.has("crossfade") ? o.get("crossfade").getAsFloat() : crossfadeSec;
            if (o.has("folders")) for (var e : o.getAsJsonArray("folders")) folders.add(Path.of(e.getAsString()));
            if (o.has("playlists")) for (var en : o.getAsJsonObject("playlists").entrySet()) {
                List<Path> l = new ArrayList<>();
                for (var e : en.getValue().getAsJsonArray()) l.add(Path.of(e.getAsString()));
                playlists.put(en.getKey(), l);
            }
            if (o.has("queue")) {
                List<Track> q = new ArrayList<>();
                for (var e : o.getAsJsonArray("queue")) {
                    Path p = Path.of(e.getAsString());
                    if (!Files.isRegularFile(p)) continue;
                    Tags t = Tags.read(p, false);
                    q.add(new Track(p, t.title, t.artist, t.album));
                }
                int i = o.has("index") ? o.get("index").getAsInt() : -1;
                long pos = o.has("position") ? o.get("position").getAsLong() : 0;
                if (!q.isEmpty() && i >= 0 && i < q.size()) {
                    synchronized (queue) { queue.addAll(q); }
                    index = i;
                    resumeAt = pos;
                }
            }
        } catch (Exception e) {
            ModuleManager.LOG.warn("Музыка: music.json не прочитан", e);
        }
    }

    private static long resumeAt = -1;

    /** Продолжить с того же места (после перезапуска): первый «играть» откроет трек на сохранённой позиции. */
    public static void resumeIfPaused() {
        if (resumeAt < 0 || index < 0) return;
        long at = resumeAt;
        resumeAt = -1;
        pendingIndex = index;
        pendingSeek = at;
    }

    public static void addFolder(Path p) {
        if (!folders.contains(p)) folders.add(p);
        save();
        Library.rescan(new ArrayList<>(folders));
    }
}
