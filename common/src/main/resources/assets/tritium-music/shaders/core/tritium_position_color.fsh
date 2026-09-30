#version 150

uniform vec4 ClipRect;

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
    vec4 color = vertexColor;
    color.a *= clipCoverage(guiPosition, ClipRect);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
