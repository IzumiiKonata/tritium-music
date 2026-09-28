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
layout(location = 4) in vec2 UV3;
layout(location = 5) in float LineWidth;

layout(location = 0) out vec2 texCoord;
layout(location = 1) out float controlPercent;
layout(location = 2) out float alpha;
layout(location = 3) out vec2 guiPosition;
layout(location = 4) flat out vec4 clipRect;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
    controlPercent = float(UV1.y) / 1024.0;
    alpha = Color.a * ColorModulator.a;
    guiPosition = Position.xy;
    clipRect = vec4(UV3, Position.z, LineWidth);
}
