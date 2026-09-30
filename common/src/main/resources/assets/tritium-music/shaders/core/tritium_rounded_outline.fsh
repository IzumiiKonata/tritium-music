#version 150

uniform vec4 ClipRect;

in vec2 localCoord;
in vec2 localPosition;
in vec4 vertexColor;
in float radius;
in float borderSize;

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

float shapeCoverage() {
    vec2 size = logicalSize();
    vec2 center = localCoord * size - size * 0.5;
    vec2 cornerDistance = abs(center) - (size * 0.5 - radius);
    float rawDistance = length(max(cornerDistance, 0.0)) - radius;
    float aa = max(length(vec2(dFdx(rawDistance), dFdy(rawDistance))) * 2.0, 0.0001);
    float shapeDistance = length(max(cornerDistance + aa, 0.0)) - radius;
    return 1.0 - smoothstep(0.0, aa, shapeDistance);
}

void main() {
    vec2 size = logicalSize();
    vec2 position = (abs(localCoord - 0.5) + 0.5) * size;
    float rawDistance = length(max(position - size + radius + borderSize, 0.0)) - radius;
    float pixel = max(length(vec2(dFdx(rawDistance), dFdy(rawDistance))), 0.0001);
    float outlineDistance = rawDistance + pixel;
    float aa = pixel * 2.0;
    float coverage = smoothstep(0.0, aa, outlineDistance) - smoothstep(0.0, aa, outlineDistance - borderSize);
    coverage = min(coverage, clipCoverage(localPosition, ClipRect));
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
