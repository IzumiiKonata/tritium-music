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
in vec2 UV3;
in float LineWidth;

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 guiPosition;
flat out vec4 clipRect;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    texCoord0 = UV0;
    vertexColor = Color * ColorModulator;
    guiPosition = Position.xy;
    clipRect = vec4(UV3, Position.z, LineWidth);
}
