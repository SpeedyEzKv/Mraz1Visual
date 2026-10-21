package dev.mraz1visual.models;

/**
 * Анимация Blockbench: каналы rotation/position/scale по костям, ключевые кадры с интерполяцией
 * linear / catmullrom / step / bezier, режимы loop / once / hold и длина.
 */
public final class Anim {
    public static final int ROT = 0, POS = 1, SCALE = 2;
    public static final int ONCE = 0, LOOP = 1, HOLD = 2;
    static final byte LINEAR = 0, CATMULL = 1, STEP = 2, BEZIER = 3;

    public final String name;
    public final float length;
    public final int loop;
    final Channel[] channels;

    Anim(String name, float length, int loop, Channel[] channels) {
        this.name = name;
        this.length = Math.max(0.05f, length);
        this.loop = loop;
        this.channels = channels;
    }

    /** Ключевые кадры одного канала одной кости. */
    static final class Channel {
        final int bone, type;
        final float[] times;
        /** [кадр][0..2] — значение «до» (pre), [кадр][3..5] — «после» (post); обычно одинаковые. */
        final Molang.Expr[][] values;
        final byte[] interp;
        /** Ручки безье: [кадр] {lt, lv.x, lv.y, lv.z, rt, rv.x, rv.y, rv.z} или null. */
        final float[][] bezier;

        Channel(int bone, int type, float[] times, Molang.Expr[][] values, byte[] interp, float[][] bezier) {
            this.bone = bone;
            this.type = type;
            this.times = times;
            this.values = values;
            this.interp = interp;
            this.bezier = bezier;
        }

        /** Значение канала в момент t (секунды) по оси axis. */
        float sample(float t, int axis) {
            int n = times.length;
            if (n == 0) return type == SCALE ? 1 : 0;
            if (t <= times[0] || n == 1) return values[0][axis].eval(t);
            if (t >= times[n - 1]) return values[n - 1][3 + axis].eval(t);
            int i = 0;
            while (i < n - 2 && t >= times[i + 1]) i++;
            float t0 = times[i], t1 = times[i + 1];
            float a = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
            float p1 = values[i][3 + axis].eval(t), p2 = values[i + 1][axis].eval(t);
            byte m0 = interp[i], m1 = interp[i + 1];
            if (m0 == STEP) return p1;
            if (m0 == BEZIER || m1 == BEZIER) {
                float[] b0 = bezier[i], b1 = bezier[i + 1];
                float c1 = p1 + (b0 != null ? b0[5 + axis] : 0);
                float c2 = p2 + (b1 != null ? b1[1 + axis] : 0);
                float u = 1 - a;
                return u * u * u * p1 + 3 * u * u * a * c1 + 3 * u * a * a * c2 + a * a * a * p2;
            }
            if (m0 == CATMULL || m1 == CATMULL) {
                float p0 = i > 0 ? values[i - 1][3 + axis].eval(t) : p1;
                float p3 = i + 2 < n ? values[i + 2][axis].eval(t) : p2;
                float a2 = a * a, a3 = a2 * a;
                return 0.5f * ((2 * p1) + (-p0 + p2) * a + (2 * p0 - 5 * p1 + 4 * p2 - p3) * a2 + (-p0 + 3 * p1 - 3 * p2 + p3) * a3);
            }
            return p1 + (p2 - p1) * a;
        }
    }

    /** Время внутри анимации с учётом режима (loop — по кругу, once/hold — до конца). */
    public float localTime(float t) {
        if (loop == LOOP) return t % length;
        return Math.min(t, length);
    }

    public boolean finished(float t) {
        return loop != LOOP && t >= length;
    }

    /**
     * Добавить позу анимации к позе костей с весом w. pose — на кость 9 чисел: rot xyz (градусы, уже со знаками
     * Blockbench), pos xyz (пиксели), scale xyz (множитель − 1, чтобы смешивание с нулём было «без масштаба»).
     */
    void apply(float t, float w, float[] pose) {
        float lt = localTime(t);
        for (Channel c : channels) {
            int o = c.bone * 9 + c.type * 3;
            if (c.type == SCALE) {
                for (int k = 0; k < 3; k++) pose[o + k] += (c.sample(lt, k) - 1) * w;
            } else {
                for (int k = 0; k < 3; k++) pose[o + k] += c.sample(lt, k) * w;
            }
        }
    }
}
