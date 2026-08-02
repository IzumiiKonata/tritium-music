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
in float LineWidth;
in ivec2 UV1;
in ivec2 UV2;

out vec2 localCoord;
out vec2 localPosition;
out vec4 vertexColor;
out float radius;
out vec2 guiPosition;
flat out ivec4 clipRectFixed;

void main() {
    vec2 corners[4] = vec2[4](vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0));
    vec4 gui = ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = ProjMat * gui;
    localCoord = corners[gl_VertexID & 3];
    localPosition = Position.xy;
    vertexColor = Color * ColorModulator;
    radius = LineWidth;
    guiPosition = gui.xy;
    clipRectFixed = ivec4(UV1, UV2);
}
