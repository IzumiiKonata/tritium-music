#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    float stencil = texture(Sampler1, texCoord0).a;
    fragColor = vec4(base.rgb, base.a * stencil) * vertexColor;
}
