#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 cornerCoord;
in float aaWidth;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float d = length(cornerCoord);
    float aa = 0.35;

    float coverage = clamp((1.0 - d) / aa + 0.5, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
