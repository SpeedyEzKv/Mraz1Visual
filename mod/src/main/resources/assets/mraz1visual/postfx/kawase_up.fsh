#version 330 core
// Dual Kawase, шаг вверх: восемь выборок «шатром» с меньшего уровня + этот же уровень из цепочки вниз.
uniform sampler2D uSrc;   // меньший уровень (уже собранный)
uniform sampler2D uSame;  // уровень того же размера из цепочки вниз
uniform vec2 uTexel;      // 1 / размер uSrc
uniform float uRadius;
uniform float uSameWeight; // доля резкого уровня: на верхнем шаге 0 — только размытое

in vec2 uv;
out vec4 fragColor;

void main() {
    vec2 o = uTexel * 0.5 * uRadius;
    vec3 s = texture(uSrc, uv + vec2(-o.x * 2.0, 0.0)).rgb;
    s += texture(uSrc, uv + vec2(-o.x, o.y)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(0.0, o.y * 2.0)).rgb;
    s += texture(uSrc, uv + vec2(o.x, o.y)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(o.x * 2.0, 0.0)).rgb;
    s += texture(uSrc, uv + vec2(o.x, -o.y)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(0.0, -o.y * 2.0)).rgb;
    s += texture(uSrc, uv + vec2(-o.x, -o.y)).rgb * 2.0;
    s /= 12.0;
    fragColor = vec4(s + texture(uSame, uv).rgb * uSameWeight, 1.0);
}
