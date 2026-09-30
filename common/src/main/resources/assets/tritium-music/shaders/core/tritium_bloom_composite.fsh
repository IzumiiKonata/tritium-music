#version 150

uniform sampler2D Sampler0;
uniform vec4 ShapeInfo;
uniform vec4 CompositeRect;
uniform float ShapeRadius;

in vec2 texCoord;

out vec4 fragColor;

float roundedDistance(vec2 point, vec4 rect, float radius) {
    vec2 halfSize = rect.zw * 0.5;
    vec2 center = rect.xy + halfSize;
    vec2 q = abs(point - center) - halfSize + vec2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    if (gl_FragCoord.x < CompositeRect.x || gl_FragCoord.x > CompositeRect.z
            || gl_FragCoord.y < CompositeRect.y || gl_FragCoord.y > CompositeRect.w) {
        discard;
    }
    if (roundedDistance(gl_FragCoord.xy, ShapeInfo, ShapeRadius) <= 0.0) {
        discard;
    }
    fragColor = vec4(0.0, 0.0, 0.0, texture(Sampler0, texCoord).a);
}
