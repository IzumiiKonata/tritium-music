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

out vec2 localCoord;
out vec2 localPosition;
out vec4 vertexColor;
out float radius;
out float borderSize;

void main() {
    vec2 corners[4] = vec2[4](vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0));
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localCoord = corners[gl_VertexID & 3];
    localPosition = Position.xy;
    vertexColor = Color * ColorModulator;
    radius = LineWidth;
    borderSize = UV0.x;
}
