#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform BlurInfo {
    vec2 Direction;
    float Radius;
    float StepWidth;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = Direction * StepWidth / vec2(textureSize(InSampler, 0));
    float sigma = Radius * 0.5;
    int halfWidth = min(int(Radius), 16);
    vec4 color = texture(InSampler, texCoord);
    float total = 1.0;
    for (int i = 1; i <= halfWidth; i++) {
        float distance = float(i);
        float weight = exp(-0.5 * distance * distance / (sigma * sigma));
        color += (texture(InSampler, texCoord + texel * distance) + texture(InSampler, texCoord - texel * distance)) * weight;
        total += 2.0 * weight;
    }
    fragColor = color / total;
}
