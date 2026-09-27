#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in float controlPercent;
layout(location = 2) in float alpha;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, vec2(texCoord.x, 1.0 - texCoord.y));
    if (textureColor.a == 0.0) {
        discard;
    }
    float gradientAlpha = alpha * (1.0 - texCoord.y / controlPercent);
    if (gradientAlpha < 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, gradientAlpha);
}
