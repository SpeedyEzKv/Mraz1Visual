#version 330 compatibility
// lines, outlines and untextured sky: vanilla look
in vec4 tint;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    color = tint;
}
