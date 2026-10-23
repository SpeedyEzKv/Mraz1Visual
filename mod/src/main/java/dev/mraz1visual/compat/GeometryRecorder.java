package dev.mraz1visual.compat;

//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// 26.2+: «немедленных» буферов кадра (MultiBufferSource) больше нет — геометрия отдаётся только через
// SubmitNodeCollector. Этот слой заменяет MultiBufferSource для мира: getBuffer(тип) пишет вершины в память,
// flush() отдаёт их игре одним submitCustomGeometry на тип. Массивы переиспользуются — мусора в кадре нет.

public final class GeometryRecorder {
    public static final GeometryRecorder WORLD = new GeometryRecorder();

    private final Map<RenderType, Rec> recs = new IdentityHashMap<>();
    private final List<Rec> order = new ArrayList<>();
    private final PoseStack identity = new PoseStack();

    // Новый кадр: прошлые вершины уже отрисованы.
    public GeometryRecorder begin() {
        for (Rec r : order) r.n = 0;
        order.clear();
        return this;
    }

    public VertexConsumer getBuffer(RenderType type) {
        Rec r = recs.computeIfAbsent(type, Rec::new);
        if (r.n == 0 && !order.contains(r)) order.add(r);
        return r;
    }

    // Отдать записанное игре (вершины уже в координатах камеры — поза единичная).
    public void flush(SubmitNodeCollector c) {
        identity.setIdentity();
        for (Rec r : order) {
            if (r.n == 0) continue;
            c.submitCustomGeometry(identity, r.type, (pose, vc) -> r.replay(vc));
        }
    }

    private static final int COLOR = 1, UV = 2, UV1 = 4, UV2 = 8, NORMAL = 16, LINE = 32;

    private static final class Rec implements VertexConsumer {
        final RenderType type;
        int n, mask;
        float[] pos = new float[3 * 256], uv = new float[2 * 256], nrm = new float[3 * 256], line = new float[256];
        int[] col = new int[256], uv1 = new int[256], uv2 = new int[256];

        Rec(RenderType type) {
            this.type = type;
        }

        private void grow() {
            int cap = col.length * 2;
            pos = java.util.Arrays.copyOf(pos, cap * 3);
            uv = java.util.Arrays.copyOf(uv, cap * 2);
            nrm = java.util.Arrays.copyOf(nrm, cap * 3);
            line = java.util.Arrays.copyOf(line, cap);
            col = java.util.Arrays.copyOf(col, cap);
            uv1 = java.util.Arrays.copyOf(uv1, cap);
            uv2 = java.util.Arrays.copyOf(uv2, cap);
        }

        void replay(VertexConsumer vc) {
            for (int i = 0; i < n; i++) {
                vc.addVertex(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
                if ((mask & COLOR) != 0) vc.setColor(col[i]);
                if ((mask & UV) != 0) vc.setUv(uv[i * 2], uv[i * 2 + 1]);
                if ((mask & UV1) != 0) vc.setUv1(uv1[i] & 0xFFFF, uv1[i] >>> 16);
                if ((mask & UV2) != 0) vc.setUv2(uv2[i] & 0xFFFF, uv2[i] >>> 16);
                if ((mask & NORMAL) != 0) vc.setNormal(nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2]);
                if ((mask & LINE) != 0) vc.setLineWidth(line[i]);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (n >= col.length) grow();
            int i = n++;
            pos[i * 3] = x;
            pos[i * 3 + 1] = y;
            pos[i * 3 + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(a << 24 | r << 16 | g << 8 | b);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            mask |= COLOR;
            col[n - 1] = argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            mask |= UV;
            uv[(n - 1) * 2] = u;
            uv[(n - 1) * 2 + 1] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            mask |= UV1;
            uv1[n - 1] = (u & 0xFFFF) | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            mask |= UV2;
            uv2[n - 1] = (u & 0xFFFF) | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            mask |= NORMAL;
            nrm[(n - 1) * 3] = x;
            nrm[(n - 1) * 3 + 1] = y;
            nrm[(n - 1) * 3 + 2] = z;
            return this;
        }

        // 26.3: третьи UV (без @Override — в 26.2 такого метода нет)
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float w) {
            mask |= LINE;
            line[n - 1] = w;
            return this;
        }
    }
}
*///?}
