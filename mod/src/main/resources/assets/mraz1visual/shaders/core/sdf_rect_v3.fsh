#version 330
#extension GL_ARB_separate_shader_objects : require
// 26.3 (renderpearl, в т.ч. Vulkan): новый порядок DynamicTransforms и явные location у входов/выходов

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 local;
layout(location = 2) flat in vec4 shape;
layout(location = 3) flat in int mode;

layout(location = 0) out vec4 fragColor;

// «Непрерывный» угол: суперэллипс степени N вместо дуги окружности — мягкий вход в скругление, как в iOS.
const float N = 2.6;

float boxDist(vec2 p, vec2 h, float r) {
    vec2 q = abs(p) - h + vec2(r);
    vec2 m = max(q, 0.0);
    float l = m.x + m.y > 0.0 ? pow(pow(m.x, N) + pow(m.y, N), 1.0 / N) : 0.0;
    return l + min(max(q.x, q.y), 0.0) - r;
}

// лёгкое зерно против полос на плавных градиентах
float grain(vec2 c) {
    return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main() {
    float d = boxDist(local, shape.xy, shape.z);
    float aa = max(fwidth(d), 1e-4);
    float a;
    if (mode == 1) {
        // кромка толщиной shape.w внутрь от контура
        a = clamp(0.5 - d / aa, 0.0, 1.0) * clamp(0.5 + (d + shape.w) / aa, 0.0, 1.0);
    } else if (mode == 2) {
        // мягкая тень: плавный спад наружу на shape.w
        float s = max(shape.w, 0.5);
        float t = clamp(1.0 - (d + s * 0.15) / s, 0.0, 1.0);
        a = t * t * (3.0 - 2.0 * t);
    } else if (mode == 3) {
        // стекло одним проходом: тонировка + блик сверху + кромка 1 px со светом сверху (shape.w — сила)
        float cover = clamp(0.5 - d / aa, 0.0, 1.0);
        if (cover <= 0.002) {
            discard;
        }
        float s = shape.w / 8.0;
        float ty = clamp((local.y + shape.y) / max(2.0 * shape.y, 1.0), 0.0, 1.0); // 0 сверху → 1 снизу
        float sheen = 0.075 * s * (1.0 - smoothstep(0.0, 0.55, ty));
        float edgeW = max(aa, 1.0);
        float rim = clamp(0.5 + (d + edgeW) / aa, 0.0, 1.0) * mix(0.36, 0.08, ty) * s;
        vec4 p = vec4(vertexColor.rgb * vertexColor.a, vertexColor.a);
        p = p * (1.0 - sheen) + vec4(sheen);
        p = p * (1.0 - rim) + vec4(rim);
        p *= cover;
        if (p.a <= 0.002) {
            discard;
        }
        vec4 outC = vec4(p.rgb / p.a, p.a);
        outC.rgb += grain(gl_FragCoord.xy) * 0.012;
        fragColor = outC * ColorModulator;
        return;
    } else {
        a = clamp(0.5 - d / aa, 0.0, 1.0);
    }
    vec4 color = vertexColor;
    color.a *= a;
    if (color.a <= 0.002) {
        discard;
    }
    color.rgb += grain(gl_FragCoord.xy) * 0.012;
    fragColor = color * ColorModulator;
}
