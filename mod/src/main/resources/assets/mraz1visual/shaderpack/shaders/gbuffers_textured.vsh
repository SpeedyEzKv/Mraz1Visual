#version 330 compatibility
out vec2 uv, lm;
out vec4 tint;

void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    tint = gl_Color;
    gl_Position = ftransform();
}
