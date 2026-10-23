#version 330 compatibility
// Soft tone curve and a touch of saturation. Sky pixels pass through untouched.
uniform sampler2D colortex0, depthtex0;
in vec2 uv;

layout(location = 0) out vec4 color;

void main() {
    vec3 c = texture(colortex0, uv).rgb;
    if (texture(depthtex0, uv).r < 1.0) {
        // shoulder: highlights roll off, mid-tones stay put
        c = c * (1.0 + c * 0.12) / (1.0 + c * 0.22);
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(vec3(l), c, 1.06);
    }
    color = vec4(c, 1.0);
}
