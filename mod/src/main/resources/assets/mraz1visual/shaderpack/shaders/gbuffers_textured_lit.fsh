#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/light.glsl"
uniform sampler2D gtexture;
uniform vec4 entityColor;
#ifdef LABPBR
uniform sampler2D specular; // LabPBR: red channel = perceptual smoothness
#endif
in vec2 uv, lm;
in vec4 tint;
in vec3 normal, viewPos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    vec4 c = texture(gtexture, uv) * tint;
    if (c.a < 0.1) discard;
    c.rgb = mix(c.rgb, entityColor.rgb, entityColor.a);
    c.rgb = shade(c.rgb, lm, normalize(normal), viewPos);
#ifdef LABPBR
    float smoothness = texture(specular, uv).r;
    if (smoothness > 0.0) {
        vec3 h = normalize(normalize(shadowLightPosition) + normalize(-viewPos));
        c.rgb += pow(max(dot(normalize(normal), h), 0.0), mix(8.0, 256.0, smoothness)) * smoothness * 0.6 * lm.y;
    }
#endif
    color = c;
}
