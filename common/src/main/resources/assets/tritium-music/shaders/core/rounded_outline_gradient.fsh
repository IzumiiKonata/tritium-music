#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 localCoord;
layout(location = 1) in vec2 localPosition;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in float radius;
layout(location = 4) in float borderSize;
layout(location = 5) in vec2 guiPosition;
layout(location = 6) flat in vec4 clipRect;

layout(location = 0) out vec4 fragColor;

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
    float noise = mix(0.5 / 255.0, -0.5 / 255.0, fract(sin(dot(localCoord, vec2(12.9898, 78.233))) * 43758.5453));
    fragColor = vec4(vertexColor.rgb + vec3(noise), vertexColor.a * coverage);
}
