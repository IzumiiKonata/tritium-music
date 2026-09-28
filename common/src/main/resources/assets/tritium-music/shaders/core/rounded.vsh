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
in vec4 Color;
in ivec2 UV1;
in vec2 UV3;
in float LineWidth;

out vec2 localCoord;
out vec2 localPosition;
out vec4 vertexColor;
out float radius;
out vec2 guiPosition;
flat out vec4 clipRect;

void main() {
    vec2 corners[4] = vec2[4](vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0));
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    localCoord = corners[gl_VertexID & 3];
    localPosition = Position.xy;
    vertexColor = Color * ColorModulator;
    radius = float(UV1.x) / 32.0;
    guiPosition = Position.xy;
    clipRect = vec4(UV3, Position.z, LineWidth);
}
