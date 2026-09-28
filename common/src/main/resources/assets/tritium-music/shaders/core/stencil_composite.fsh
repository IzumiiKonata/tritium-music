#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 guiPosition;
flat in vec4 clipRect;

out vec4 fragColor;

void main() {
    vec2 outside = max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
    float distance = max(outside.x, outside.y);
    float aa = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / aa, 0.0, 1.0);
    if (coverage <= 0.0) {
        discard;
    }
    vec4 base = texture(Sampler0, texCoord0);
    float stencil = texture(Sampler1, texCoord0).a;
    fragColor = vec4(base.rgb * vertexColor.rgb, base.a) * (stencil * vertexColor.a) * coverage;
}
