#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/light.glsl"
uniform sampler2D gtexture;
uniform float frameTimeCounter;
uniform mat4 gbufferModelView;
uniform vec3 skyColor;
in vec2 uv, lm;
in vec4 tint;
in vec3 normal, viewPos, worldPos;
in float isWater;

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 color;
layout(location = 1) out vec4 waterData;

// small ripples: two layers of sines in world space, normal from derivatives
vec3 waveNormal(vec2 p, float t) {
    float a = p.x * 1.3 + t * 1.1, b = p.y * 1.1 - t * 0.9, c = p.x * 3.1 - t * 1.7 + p.y * 2.3;
    float dx = cos(a) * 1.3 * cos(b) + cos(c) * 1.55;
    float dy = -sin(a) * sin(b) * 1.1 + cos(c) * 1.15;
    return normalize(vec3(-dx * 0.04, 1.0, -dy * 0.04));
}

void main() {
    vec4 c = texture(gtexture, uv) * tint;
    if (isWater < 0.5) {
        // glass, ice and other translucent blocks: lit like regular blocks
        c.rgb = shade(c.rgb, lm, normalize(normal), viewPos);
        color = c;
        waterData = vec4(0.0);
        return;
    }
    vec3 n = normalize(mat3(gbufferModelView) * waveNormal(worldPos.xz, frameTimeCounter));
    vec3 v = normalize(-viewPos);
    // Schlick fresnel: reflective at grazing angles, clear from above
    float fres = 0.04 + 0.96 * pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 5.0);
    vec3 body = shade(c.rgb * vec3(0.55, 0.78, 0.9), lm, n, viewPos);
    vec3 sky = skyColor * clamp(lm.y * 1.2, 0.0, 1.0);
    // sun glints on wave crests
    float glint = pow(clamp(dot(reflect(-normalize(shadowLightPosition), n), v), 0.0, 1.0), 80.0) * lm.y;
    color = vec4(mix(body, sky, fres) + glint * 0.8, mix(WATER_ALPHA, 0.95, fres));
    waterData = vec4(n * 0.5 + 0.5, fres);
}
