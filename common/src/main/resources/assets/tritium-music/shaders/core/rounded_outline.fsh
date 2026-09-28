#version 330

in vec2 localCoord;
in vec2 localPosition;
in vec4 vertexColor;
in float radius;
in float borderSize;
in vec2 guiPosition;
flat in vec4 clipRect;

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

float clipCoverage() {
    vec2 outside = max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
    float distance = max(outside.x, outside.y);
    float aa = max(fwidth(distance), 0.0001);
    return clamp(0.5 - distance / aa, 0.0, 1.0);
}

void main() {
    vec2 size = logicalSize();
    vec2 position = (abs(localCoord - 0.5) + 0.5) * size;
    float rawDistance = length(max(position - size + radius + borderSize, 0.0)) - radius;
    float pixel = max(length(vec2(dFdx(rawDistance), dFdy(rawDistance))), 0.0001);
    float distance = rawDistance + pixel;
    float aa = pixel * 2.0;
    float coverage = smoothstep(0.0, aa, distance) - smoothstep(0.0, aa, distance - borderSize);
    coverage = min(coverage, clipCoverage());
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
