#version 330 core
// Dual Kawase, шаг вниз: центр ×4 + четыре диагональные выборки, результат на уровень меньше.
// На первом шаге (uThreshold > 0) — мягкий порог: в свечение идёт только яркое.
uniform sampler2D uSrc;
uniform vec2 uTexel;      // 1 / размер источника
uniform float uThreshold; // 0 — без порога
uniform float uKnee;

in vec2 uv;
out vec4 fragColor;

vec3 prefilter(vec3 c) {
    float br = max(c.r, max(c.g, c.b));
    float soft = clamp(br - uThreshold + uKnee, 0.0, 2.0 * uKnee);
    soft = soft * soft / (4.0 * uKnee + 1e-4);
    float w = max(soft, br - uThreshold) / max(br, 1e-4);
    return c * w;
}

void main() {
    vec2 o = uTexel * 0.5;
    vec3 s = texture(uSrc, uv).rgb * 4.0;
    s += texture(uSrc, uv + vec2(-o.x, -o.y)).rgb;
    s += texture(uSrc, uv + vec2(o.x, -o.y)).rgb;
    s += texture(uSrc, uv + vec2(-o.x, o.y)).rgb;
    s += texture(uSrc, uv + vec2(o.x, o.y)).rgb;
    s *= 0.125;
    if (uThreshold > 0.0) s = prefilter(s);
    fragColor = vec4(s, 1.0);
}
