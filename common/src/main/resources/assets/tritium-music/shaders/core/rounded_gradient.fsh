#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 localCoord;
layout(location = 1) in vec2 localPosition;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in float radius;
layout(location = 4) in vec2 guiPosition;
layout(location = 5) flat in ivec4 clipRectFixed;

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
    vec2 size = logicalSize();
    vec2 center = localCoord * size - size * 0.5;
    vec2 cornerDistance = abs(center) - (size * 0.5 - radius);
    float rawDistance = length(max(cornerDistance, 0.0)) - radius;
    float aa = max(length(vec2(dFdx(rawDistance), dFdy(rawDistance))) * 2.0, 0.0001);
    float distance = length(max(cornerDistance + aa, 0.0)) - radius;
    float coverage = 1.0 - smoothstep(0.0, aa, distance);
    float noise = mix(0.5 / 255.0, -0.5 / 255.0, fract(sin(dot(localCoord, vec2(12.9898, 78.233))) * 43758.5453));
    fragColor = vec4(vertexColor.rgb + vec3(noise), coverage * clipCoverage());
}
