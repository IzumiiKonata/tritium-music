#version 150

uniform sampler2D Sampler0;
uniform vec4 BlurInfo;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 direction = BlurInfo.xy;
    float radius = BlurInfo.z;
    float stepWidth = BlurInfo.w;
    vec2 texel = direction * stepWidth / vec2(textureSize(Sampler0, 0));
    float sigma = radius * 0.5;
    int halfWidth = min(int(radius), 16);
    vec4 color = texture(Sampler0, texCoord);
    float total = 1.0;
    for (int i = 1; i <= halfWidth; i++) {
        float offset = float(i);
        float weight = exp(-0.5 * offset * offset / (sigma * sigma));
        color += (texture(Sampler0, texCoord + texel * offset) + texture(Sampler0, texCoord - texel * offset)) * weight;
        total += 2.0 * weight;
    }
    fragColor = color / total;
}
