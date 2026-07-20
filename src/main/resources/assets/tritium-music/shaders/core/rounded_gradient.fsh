#version 330

in vec2 localCoord;
in vec2 localPosition;
in vec4 vertexColor;
in float radius;

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

void main() {
    vec2 size = logicalSize();
    vec2 center = localCoord * size - size * 0.5;
    float distance = length(max(abs(center) - (size * 0.5 - radius - 1.0), 0.0)) - radius;
    float coverage = 1.0 - smoothstep(0.0, 1.0, distance);
    float noise = mix(0.5 / 255.0, -0.5 / 255.0, fract(sin(dot(localCoord, vec2(12.9898, 78.233))) * 43758.5453));
    fragColor = vec4(vertexColor.rgb + vec3(noise), coverage);
}
