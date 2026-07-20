#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;
in vec2 guiPosition;
flat in ivec4 clipRectFixed;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    vec4 clipRect = vec4(clipRectFixed) / 8.0;
    vec2 inside = min(guiPosition - clipRect.xy, clipRect.zw - guiPosition);
    float distance = min(inside.x, inside.y);
    if (distance < 0.0) {
        discard;
    }
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
