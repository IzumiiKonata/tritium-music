#version 330

in vec2 localCoord;
in vec2 localPosition;
in vec4 vertexColor;
in float radius;
in vec2 guiPosition;
flat in ivec4 clipRectFixed;

out vec4 fragColor;

vec2 logicalSize() {
    vec2 positionDx = dFdx(localPosition);
    vec2 positionDy = dFdy(localPosition);
    vec2 coordDx = dFdx(localCoord);
    vec2 coordDy = dFdy(localCoord);
    return vec2(
        length(vec2(positionDx.x, positionDy.x)) / max(length(vec2(coordDx.x, coordDy.x)), 0.000001),
        length(vec2(positionDx.y, positionDy.y)) / max(length(vec2(coordDx.y, coordDy.y)), 0.000001)
    );
}

float roundedCoverage(vec2 size) {
    vec2 center = localCoord * size - size * 0.5;
    float distance = length(max(abs(center) - (size * 0.5 - radius - 1.0), 0.0)) - radius;
    return 1.0 - smoothstep(0.0, 1.0, distance);
}

float clipCoverage() {
    vec4 clipRect = vec4(clipRectFixed) / 8.0;
    vec2 inside = min(guiPosition - clipRect.xy, clipRect.zw - guiPosition);
    float distance = min(inside.x, inside.y);
    if (distance < 0.0) {
        return 0.0;
    }
    float aa = max(fwidth(distance), 0.0001);
    return smoothstep(0.0, aa, distance);
}

void main() {
    vec4 color = vertexColor;
    color.a *= roundedCoverage(logicalSize()) * clipCoverage();
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
