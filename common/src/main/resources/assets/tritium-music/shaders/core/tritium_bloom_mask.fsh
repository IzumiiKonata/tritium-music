#version 150

uniform vec4 ShapeInfo;
uniform float ShapeRadius;
uniform float ShapeOpacity;

out vec4 fragColor;

float roundedDistance(vec2 point, vec4 rect, float radius) {
    vec2 halfSize = rect.zw * 0.5;
    vec2 center = rect.xy + halfSize;
    vec2 q = abs(point - center) - halfSize + vec2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float shapeDistance = roundedDistance(gl_FragCoord.xy, ShapeInfo, ShapeRadius);
    float coverage = 1.0 - smoothstep(-0.5, 0.5, shapeDistance);
    fragColor = vec4(0.0, 0.0, 0.0, coverage * ShapeOpacity);
}
