#version 330

// Фигуры GUI Mraz1Visual: квад + параметры фигуры, расстояние считается во фрагментном шейдере.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;   // точка относительно центра фигуры (единицы GUI)
in ivec2 UV1;  // полуразмеры ×4
in ivec2 UV2;  // радиус ×4; (параметр ×4) << 3 | режим

out vec4 vertexColor;
out vec2 local;
flat out vec4 shape;   // полуширина, полувысота, радиус, параметр
flat out int mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    shape = vec4(vec2(UV1) / 4.0, float(UV2.x) / 4.0, float(UV2.y >> 3) / 4.0);
    mode = UV2.y & 7;
}
