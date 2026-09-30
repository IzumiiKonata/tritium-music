#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Radius;

out vec2 localCoord;
out vec2 localPosition;
out vec4 vertexColor;
out float radius;

void main() {
    vec2 corners[4] = vec2[4](vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0));
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localCoord = corners[gl_VertexID & 3];
    localPosition = Position.xy;
    vertexColor = Color;
    radius = Radius;
}
