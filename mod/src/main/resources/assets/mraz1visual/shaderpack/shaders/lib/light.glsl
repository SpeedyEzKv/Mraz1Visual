// Mraz1 Shaders lighting: vanilla lightmap + PCF sun shadow + warm colored block light.
#include "/lib/shadow_distort.glsl"
uniform sampler2D lightmap;
uniform sampler2DShadow shadowtex0;
uniform mat4 gbufferModelViewInverse, shadowModelView, shadowProjection;
uniform vec3 shadowLightPosition;
uniform float rainStrength;
uniform int worldTime;

float sunVisibility(vec3 viewPos, vec3 normal, float skyLight) {
    if (skyLight < 0.05) return 0.0;
    vec3 L = normalize(shadowLightPosition);
    float ndl = dot(normal, L);
    if (ndl <= 0.0) return 0.0;
    vec4 world = gbufferModelViewInverse * vec4(viewPos, 1.0);
    // normal offset against shadow acne
    world.xyz += mat3(gbufferModelViewInverse) * normal * 0.06;
    vec4 sp = shadowProjection * (shadowModelView * world);
    sp.xyz = distortShadow(sp.xyz);
    vec3 st = sp.xyz * 0.5 + 0.5;
    if (any(lessThan(st, vec3(0.0))) || any(greaterThan(st, vec3(1.0)))) return ndl;
    float bias = 0.0006;
#if SHADOW_QUALITY == 0
    float s = texture(shadowtex0, vec3(st.xy, st.z - bias));
#else
    float texel = 1.0 / float(shadowMapResolution);
    float s = 0.0;
    for (int x = -SHADOW_QUALITY; x <= SHADOW_QUALITY; x++)
        for (int y = -SHADOW_QUALITY; y <= SHADOW_QUALITY; y++)
            s += texture(shadowtex0, vec3(st.xy + vec2(x, y) * texel, st.z - bias));
    s /= float((2 * SHADOW_QUALITY + 1) * (2 * SHADOW_QUALITY + 1));
#endif
    return s * ndl;
}

vec3 shade(vec3 albedo, vec2 lm, vec3 normal, vec3 viewPos) {
    vec3 light = texture(lightmap, lm).rgb;
    float sky = clamp((lm.y * 33.05 - 1.05) / 32.0, 0.0, 1.0);
    float block = clamp((lm.x * 33.05 - 1.05) / 32.0, 0.0, 1.0);
#ifdef COLORED_LIGHT
    // torch and lantern light is warmer where there is little sky light
    light *= mix(vec3(1.0), vec3(1.18, 0.96, 0.74), block * block * (1.0 - sky * 0.6));
#endif
    float day = clamp(sin(float(worldTime) / 24000.0 * 6.2831853) * 2.0 + 0.4, 0.0, 1.0) * (1.0 - rainStrength * 0.8);
    float sun = sunVisibility(viewPos, normal, sky);
    // shadowed areas are slightly darker and cooler, sunlit ones get a warm key light
    float shadowMix = sky * day * 0.42;
    vec3 lit = light * (1.0 - shadowMix) + light * shadowMix * (sun * 1.55 * SUN_STRENGTH) * vec3(1.06, 1.0, 0.92);
    lit = mix(lit, lit * vec3(0.92, 0.97, 1.08), (1.0 - sun) * shadowMix);
    return albedo * lit;
}
