#version 330 compatibility
#include "/lib/settings.glsl"
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
uniform float frameTimeCounter;
uniform mat4 gbufferModelView, gbufferModelViewInverse;
uniform vec3 cameraPosition;
out vec2 uv, lm;
out vec4 tint;
out vec3 normal, viewPos;

void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    tint = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
#ifdef WAVING_PLANTS
    float id = mc_Entity.x;
    bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t; // only the top of the plant sways, roots stay
    if (id == 10.0 || (id == 11.0 && top)) {
        vec4 world = gbufferModelViewInverse * view;
        vec3 wp = world.xyz + cameraPosition;
        float t = frameTimeCounter * 1.6;
        float amp = (id == 10.0 ? 0.035 : 0.07) * lm.y;
        world.x += sin(t + wp.x * 0.7 + wp.z * 0.4) * amp;
        world.z += cos(t * 0.8 + wp.z * 0.6 + wp.y * 0.3) * amp;
        view = gbufferModelView * world;
    }
#endif
    viewPos = view.xyz;
    gl_Position = gl_ProjectionMatrix * view;
}
