#version 330 compatibility
in vec4 mc_Entity;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
out vec2 uv, lm;
out vec4 tint;
out vec3 normal, viewPos, worldPos;
out float isWater;

void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    tint = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    viewPos = view.xyz;
    worldPos = (gbufferModelViewInverse * view).xyz + cameraPosition;
    isWater = mc_Entity.x == 20.0 ? 1.0 : 0.0;
    gl_Position = gl_ProjectionMatrix * view;
}
