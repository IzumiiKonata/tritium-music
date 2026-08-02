#version 330

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
in vec2 UV0;
in vec4 Color;
in ivec2 UV1;
in ivec2 UV2;

out vec2 texCoord;
out vec4 vertexColor;
out vec2 guiPosition;
flat out ivec4 clipRectFixed;

void main() {
    vec4 gui = ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = ProjMat * gui;
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
    guiPosition = Position.xy;
    clipRectFixed = ivec4(UV1, UV2);
}
