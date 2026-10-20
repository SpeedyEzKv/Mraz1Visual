#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 local;
flat in vec4 shape;
flat in int mode;

out vec4 fragColor;

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
