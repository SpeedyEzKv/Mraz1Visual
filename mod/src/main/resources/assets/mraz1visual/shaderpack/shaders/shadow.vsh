#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/shadow_distort.glsl"
attribute vec4 mc_Entity;
uniform float frameTimeCounter;
uniform mat4 shadowModelView, shadowModelViewInverse;
out vec2 uv;
out vec4 tint;
void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    tint = gl_Color;
    vec4 pos = ftransform();
    pos.xyz = distortShadow(pos.xyz);
    gl_Position = pos;
}
