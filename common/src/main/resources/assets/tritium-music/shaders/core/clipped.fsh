#version 330

in vec4 vertexColor;
in vec2 guiPosition;
flat in vec4 clipRect;

out vec4 fragColor;

void main() {
    vec2 outside = max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
    float distance = max(outside.x, outside.y);
    float aa = max(fwidth(distance), 0.0001);
    vec4 color = vertexColor;
    color.a *= clamp(0.5 - distance / aa, 0.0, 1.0);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
