#version 330
#extension GL_ARB_separate_shader_objects : require
// 26.3 (renderpearl, в т.ч. Vulkan): новый порядок DynamicTransforms и явные location у входов/выходов

// Фигуры GUI Mraz1Visual: квад + параметры фигуры, расстояние считается во фрагментном шейдере.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;   // точка относительно центра фигуры (единицы GUI)
layout(location = 3) in ivec2 UV1;  // полуразмеры ×4
layout(location = 4) in ivec2 UV2;  // радиус ×4; (параметр ×4) << 3 | режим

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 local;
layout(location = 2) flat out vec4 shape;   // полуширина, полувысота, радиус, параметр
layout(location = 3) flat out int mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    shape = vec4(vec2(UV1) / 4.0, float(UV2.x) / 4.0, float(UV2.y >> 3) / 4.0);
    mode = UV2.y & 7;
}
