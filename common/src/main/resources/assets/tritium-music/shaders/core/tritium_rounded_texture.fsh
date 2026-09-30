#version 150

uniform sampler2D Sampler0;
uniform vec4 ClipRect;

in vec2 texCoord;
in vec2 localCoord;
in vec2 localPosition;
in float alpha;
in float radius;

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
    vec4 textureColor = texture(Sampler0, texCoord);
    float coverage = alpha * min(shapeCoverage(), clipCoverage(localPosition, ClipRect));
    if (textureColor.a <= 0.0 || coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, coverage);
}
