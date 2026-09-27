#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 guiPosition;
layout(location = 3) flat in ivec4 clipRectFixed;

layout(location = 0) out vec4 fragColor;

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
