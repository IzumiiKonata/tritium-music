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
in float LineWidth;

out vec2 texCoord;
out float controlPercent;
out float alpha;
out vec2 guiPosition;
flat out vec4 clipRect;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
    controlPercent = float(UV1.y) / 1024.0;
    alpha = Color.a * ColorModulator.a;
    guiPosition = Position.xy;
    clipRect = vec4(vec2(UV2) / 8.0, Position.z, LineWidth);
}
