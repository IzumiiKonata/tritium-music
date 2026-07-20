#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform BlurInfo {
    vec2 Direction;
    float Radius;
    float StepWidth;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = Direction * StepWidth / vec2(textureSize(InSampler, 0));
    float sigma = Radius * 0.5;
    float total = 0.0;
    vec4 color = vec4(0.0);
    for (int i = -16; i <= 16; i++) {
        float distance = float(i);
        if (abs(distance) <= Radius) {
            float weight = exp(-0.5 * distance * distance / (sigma * sigma));
            color += texture(InSampler, texCoord + texel * distance) * weight;
            total += weight;
        }
    }
    fragColor = color / total;
}
