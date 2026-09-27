#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

layout(std140) uniform ShapeInfo {
    vec4 Rect;
    float Radius;
    float Opacity;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float roundedDistance(vec2 point, vec4 rect, float radius) {
    vec2 halfSize = rect.zw * 0.5;
    vec2 center = rect.xy + halfSize;
    vec2 q = abs(point - center) - halfSize + vec2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float distance = roundedDistance(gl_FragCoord.xy, Rect, Radius);
    float coverage = 1.0 - smoothstep(-0.5, 0.5, distance);
    fragColor = vec4(0.0, 0.0, 0.0, coverage * Opacity);
}
