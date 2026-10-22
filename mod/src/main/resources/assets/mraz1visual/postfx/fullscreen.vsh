#version 330 core
// Полноэкранный треугольник без вершинных буферов: три вершины из gl_VertexID.
out vec2 uv;

void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
