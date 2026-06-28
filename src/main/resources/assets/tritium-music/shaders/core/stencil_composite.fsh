#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    float stencil = texture(Sampler1, texCoord0).a;
    fragColor = vec4(base.rgb, base.a * stencil) * vertexColor;
}
