#version 330 compatibility
// particles, clouds and sky textures: vanilla look (sky is not changed)
uniform sampler2D gtexture;
uniform sampler2D lightmap;
in vec2 uv, lm;
in vec4 tint;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    vec4 c = texture(gtexture, uv) * tint;
    if (c.a < 0.01) discard;
    color = c * vec4(texture(lightmap, lm).rgb, 1.0);
}
