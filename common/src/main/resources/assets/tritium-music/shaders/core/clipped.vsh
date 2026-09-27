#version 330
#extension GL_ARB_separate_shader_objects : require

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
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 guiPosition;
layout(location = 2) flat out ivec4 clipRectFixed;

void main() {
    vec4 gui = ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = ProjMat * gui;
    vertexColor = Color * ColorModulator;
    guiPosition = Position.xy;
    clipRectFixed = ivec4(UV1, UV2);
}
