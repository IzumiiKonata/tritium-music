#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ClipRect;

in vec2 texCoord0;
in vec4 vertexColor;
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
    if (coverage <= 0.0) {
        discard;
    }
    vec4 base = texture(Sampler0, texCoord0);
    float stencil = texture(Sampler1, texCoord0).a;
    fragColor = vec4(base.rgb * vertexColor.rgb, base.a) * (stencil * vertexColor.a) * coverage;
}
