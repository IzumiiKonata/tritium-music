#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 cornerCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // cornerCoord is the position inside a corner quad, where the arc centre is at
    // the origin and the radius is 1.0. Body quads pass (0,0) so the SDF is fully inside.
    float d = length(cornerCoord);
    float aa = max(fwidth(d), 1e-4);
    float coverage = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, d);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
