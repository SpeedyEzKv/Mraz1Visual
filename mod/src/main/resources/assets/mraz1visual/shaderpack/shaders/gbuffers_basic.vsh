#version 330 compatibility
out vec4 tint;

void main() {
    tint = gl_Color;
    gl_Position = ftransform();
}
