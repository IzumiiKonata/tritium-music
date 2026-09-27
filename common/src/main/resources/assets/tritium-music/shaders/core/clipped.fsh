#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 guiPosition;
layout(location = 2) flat in ivec4 clipRectFixed;

layout(location = 0) out vec4 fragColor;

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
