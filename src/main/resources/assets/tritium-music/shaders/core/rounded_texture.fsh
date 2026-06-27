#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec2 cornerCoord;
in float aaWidth;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float d = length(cornerCoord);
    float aa = max(aaWidth, 1e-6);

    float coverage = clamp((1.0 - d) / aa + 0.5, 0.0, 1.0);

    vec4 texColor = texture(Sampler0, texCoord);
    vec4 color = vertexColor * ColorModulator * texColor;
    color.a *= coverage;

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
