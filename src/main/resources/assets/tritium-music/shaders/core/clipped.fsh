#version 330

in vec4 vertexColor;
in vec2 guiPosition;
flat in ivec4 clipRectFixed;

out vec4 fragColor;

void main() {
    vec4 clipRect = vec4(clipRectFixed) / 8.0;
    vec2 inside = min(guiPosition - clipRect.xy, clipRect.zw - guiPosition);
    float distance = min(inside.x, inside.y);
    if (distance < 0.0) {
        discard;
    }
    vec4 color = vertexColor;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
