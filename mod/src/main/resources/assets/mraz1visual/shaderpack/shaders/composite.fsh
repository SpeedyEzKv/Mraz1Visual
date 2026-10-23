#version 330 compatibility
// Water reflections (SSR): march the reflected ray in screen space
// until it hits geometry (depthtex1 = depth without water). No hit: keep the sky reflection from gbuffers_water.
#include "/lib/settings.glsl"
uniform sampler2D colortex0, colortex1, depthtex0, depthtex1;
uniform mat4 gbufferProjection, gbufferProjectionInverse;
in vec2 uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

vec3 viewAt(vec2 st, float depth) {
    vec4 p = gbufferProjectionInverse * vec4(vec3(st, depth) * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    vec4 base = texture(colortex0, uv);
    vec4 water = texture(colortex1, uv);
    color = base;
    if (water.a <= 0.0) return;
    float d = texture(depthtex0, uv).r;
    vec3 vp = viewAt(uv, d);
    vec3 n = normalize(water.xyz * 2.0 - 1.0);
    vec3 r = normalize(reflect(normalize(vp), n));
    if (r.z > 0.0) return; // ray points at the camera: nothing to reflect
    float stepLen = max(0.4, -vp.z * 0.06);
    vec3 p = vp;
    for (int i = 0; i < SSR_STEPS; i++) {
        p += r * stepLen;
        vec4 clip = gbufferProjection * vec4(p, 1.0);
        vec2 st = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(st, vec2(0.0))) || any(greaterThan(st, vec2(1.0)))) break;
        float sd = texture(depthtex1, st).r;
        if (sd >= 1.0) continue;
        vec3 hit = viewAt(st, sd);
        float diff = hit.z - p.z;
        if (diff > 0.0 && diff < stepLen * 2.5) {
            float edge = 1.0 - smoothstep(0.8, 1.0, max(abs(st.x * 2.0 - 1.0), abs(st.y * 2.0 - 1.0)));
            color.rgb = mix(base.rgb, texture(colortex0, st).rgb, water.a * 0.85 * edge);
            return;
        }
        stepLen *= 1.12;
    }
}
