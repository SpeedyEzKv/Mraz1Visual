#version 330 compatibility
uniform sampler2D gtexture;
in vec2 uv;
in vec4 tint;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;
void main() {
    color = texture(gtexture, uv) * tint;
    if (color.a < 0.1) discard;
}
