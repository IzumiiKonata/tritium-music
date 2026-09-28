#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in float controlPercent;
layout(location = 2) in float alpha;
layout(location = 3) in vec2 guiPosition;
layout(location = 4) flat in vec4 clipRect;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 outside = max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
    float distance = max(outside.x, outside.y);
    float aa = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / aa, 0.0, 1.0);
    vec4 textureColor = texture(Sampler0, vec2(texCoord.x, 1.0 - texCoord.y));
    float gradientAlpha = alpha * (1.0 - texCoord.y / controlPercent) * coverage;
    if (textureColor.a <= 0.0 || gradientAlpha <= 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, gradientAlpha);
}
