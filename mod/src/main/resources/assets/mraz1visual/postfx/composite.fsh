#version 330 core
// Mraz1 PostFX: финальный проход поверх кадра мира (до HUD и меню).
// Порядок: подводное искажение → SSAO-lite → DOF → туман → лучи солнца → свечение → экспозиция и тонмаппинг
// (ACES / AgX) → цветокоррекция пресета → хроматическая аберрация → виньетка → зерно.
// Небо (глубина = 1) по умолчанию не трогаем: его вид задаёт игра и модуль неба.
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uDepth;
uniform sampler2D uBlur;

uniform vec2 uRes;
uniform float uTime;
uniform vec2 uNearFar;

uniform float uExposure;
uniform int uTonemap;        // 0 — нет, 1 — ACES, 2 — AgX
uniform float uBloomStrength;
uniform vec3 uLift;
uniform vec3 uGamma;
uniform vec3 uGain;
uniform float uSat;
uniform float uContrast;
uniform float uVignette;
uniform float uGrain;
uniform float uCA;
uniform float uAO;
uniform int uAOSamples;
uniform float uFog;
uniform vec3 uFogColor;
uniform vec3 uSun;           // xy — солнце на экране (0..1), z — видимость
uniform float uRays;
uniform int uRaySamples;
uniform float uDof;
uniform float uDofRange;
uniform float uWater;
uniform float uSkyKeep;

in vec2 uv;
out vec4 fragColor;

float linearDepth(float d) {
    float n = uNearFar.x, f = uNearFar.y;
    float z = d * 2.0 - 1.0;
    return 2.0 * n * f / (f + n - z * (f - n));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }

// ACES (аппроксимация Нарковича), нормированная так, чтобы средне-серый остался на месте
vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// AgX: логарифмическое сжатие в своём базисе и мягкая «плечевая» кривая
vec3 agx(vec3 c) {
    const mat3 inM = mat3(0.842479, 0.0423282, 0.0423756, 0.0784336, 0.878468, 0.0784336, 0.0792237, 0.0791661, 0.879142);
    const mat3 outM = mat3(1.19688, -0.0528968, -0.0529716, -0.0980209, 1.15190, -0.0980435, -0.0990297, -0.0989612, 1.15107);
    c = inM * max(c, 1e-6);
    c = clamp(log2(c), -12.47393, 4.026069);
    c = (c + 12.47393) / 16.5;
    vec3 x2 = c * c, x4 = x2 * x2;
    c = 15.5 * x4 * x2 - 40.14 * x4 * c + 31.96 * x4 - 6.868 * x2 * c + 0.4298 * x2 + 0.1191 * c - 0.00232;
    c = outM * c;
    return pow(max(c, 0.0), vec3(2.2));
}

void main() {
    vec2 st = uv;
    // под водой: лёгкая волна картинки
    if (uWater > 0.0) {
        st += uWater * 0.0035 * vec2(sin(uv.y * 38.0 + uTime * 2.1), cos(uv.x * 31.0 + uTime * 1.7));
    }
    float depth = texture(uDepth, st).r;
    bool sky = depth >= 0.99999;
    float lin = linearDepth(depth);
    vec3 base = texture(uScene, st).rgb;
    vec3 col = base;

    // SSAO-lite: соседи заметно ближе к камере — точка в «углу»
    if (uAO > 0.0 && !sky && uAOSamples > 0) {
        float occ = 0.0;
        float r = clamp(10.0 / lin, 0.6, 7.0);
        float rot = hash(gl_FragCoord.xy) * 6.2831;
        for (int i = 0; i < uAOSamples; i++) {
            float a = rot + float(i) * 2.39996;
            float k = (float(i) + 1.0) / float(uAOSamples);
            vec2 off = vec2(cos(a), sin(a)) * r * k / uRes * 8.0;
            float ld = linearDepth(texture(uDepth, st + off).r);
            float diff = lin - ld;
            occ += smoothstep(0.08, 0.6, diff) * (1.0 - smoothstep(1.5, 3.0, diff));
        }
        occ /= float(uAOSamples);
        col *= 1.0 - occ * uAO * 0.75;
    }

    // глубина резкости: фокус — то, что в центре экрана; рука и ближний план не размываются
    if (uDof > 0.0) {
        float focus = linearDepth(texture(uDepth, vec2(0.5)).r);
        float coc = clamp(abs(lin - focus) / (focus * uDofRange + 1.0), 0.0, 1.0);
        if (lin < 0.6) coc = 0.0;
        col = mix(col, texture(uBlur, st).rgb, coc * uDof);
    }
    if (uWater > 0.0) {
        col = mix(col, texture(uBlur, st).rgb, 0.35 * uWater);
        col = mix(col, col * vec3(0.55, 0.85, 1.05) + vec3(0.0, 0.03, 0.06), uWater);
    }

    // туман по глубине (только геометрия)
    if (uFog > 0.0 && !sky) {
        float f = 1.0 - exp(-lin * uFog * 0.01);
        col = mix(col, uFogColor, clamp(f, 0.0, 0.9));
    }

    // лучи солнца: радиальное размытие ярких точек неба в сторону солнца
    if (uRays > 0.0 && uSun.z > 0.0 && uRaySamples > 0) {
        vec2 dir = (uSun.xy - st) / float(uRaySamples) * 0.85;
        vec2 p = st;
        float illum = 0.0, decay = 1.0;
        for (int i = 0; i < uRaySamples; i++) {
            p += dir;
            float d = texture(uDepth, p).r;
            vec3 s = texture(uScene, p).rgb;
            float br = d >= 0.99999 ? max(dot(s, vec3(0.299, 0.587, 0.114)) - 0.55, 0.0) : 0.0;
            illum += br * decay;
            decay *= 0.94;
        }
        illum /= float(uRaySamples);
        float fall = 1.0 - smoothstep(0.0, 0.9, distance(st, uSun.xy));
        col += vec3(1.0, 0.86, 0.66) * illum * uRays * uSun.z * fall * (sky ? 0.35 : 1.0) * 2.5;
    }

    // HDR-ветка: экспозиция, свечение, тонмаппинг
    vec3 bloomLin = texture(uBloom, st).rgb * uBloomStrength * 0.9;
    vec3 raysCol = col - base; // вклад лучей (до тонмаппинга)
    vec3 lc = toLinear(col) * uExposure;
    lc += bloomLin;
    if (uTonemap == 1) lc = aces(lc * 1.4);
    else if (uTonemap == 2) lc = agx(lc * 1.1);
    col = toSrgb(lc);

    // цветокоррекция пресета (lift / gamma / gain, насыщенность, контраст)
    col = uGain * (col + uLift * (1.0 - col));
    col = pow(max(col, 0.0), 1.0 / max(uGamma, vec3(0.05)));
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, uSat);
    col = (col - 0.5) * uContrast + 0.5;

    // хроматическая аберрация к краям
    if (uCA > 0.0) {
        vec2 dc = (st - 0.5) * uCA * 0.006;
        float r = texture(uScene, st + dc).r, b = texture(uScene, st - dc).b;
        col.r += (r - base.r) * 0.8;
        col.b += (b - base.b) * 0.8;
    }

    // небо: вернуть как было (по желанию игрока)
    // небо: без цветокоррекции, но со свечением солнца и лучами
    if (sky) col = mix(col, toSrgb(toLinear(base) + bloomLin * 0.6) + max(raysCol, 0.0), uSkyKeep);

    // виньетка и зерно
    vec2 q = uv - 0.5;
    col *= 1.0 - uVignette * smoothstep(0.25, 0.85, dot(q, q) * 2.2);
    col += (hash(gl_FragCoord.xy + fract(uTime) * 91.7) - 0.5) * uGrain * 0.08;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
