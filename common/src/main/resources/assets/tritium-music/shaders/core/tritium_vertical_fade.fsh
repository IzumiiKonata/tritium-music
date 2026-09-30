#version 150

uniform sampler2D Sampler0;
uniform vec4 ClipRect;

in vec2 texCoord;
in float controlPercent;
in float alpha;
in vec2 guiPosition;

out vec4 fragColor;
vec2 outsideDistance(vec2 guiPosition, vec4 clipRect) {
    return max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
}

float clipCoverage(vec2 guiPosition, vec4 clipRect) {
    vec2 outside = outsideDistance(guiPosition, clipRect);
    float outsideAmount = max(outside.x, outside.y);
    float aa = max(fwidth(outsideAmount), 0.0001);
    return clamp(0.5 - outsideAmount / aa, 0.0, 1.0);
}
void main() {
    float coverage = clipCoverage(guiPosition, ClipRect);
    vec4 textureColor = texture(Sampler0, vec2(texCoord.x, 1.0 - texCoord.y));
    float gradientAlpha = alpha * (1.0 - texCoord.y / controlPercent) * coverage;
    if (textureColor.a <= 0.0 || gradientAlpha <= 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, gradientAlpha);
}
