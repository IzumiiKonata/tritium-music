#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in float controlPercent;
in float alpha;
in vec2 guiPosition;
flat in vec4 clipRect;

out vec4 fragColor;

void main() {
    vec2 outside = max(clipRect.xy - guiPosition, guiPosition - clipRect.zw);
    float distance = max(outside.x, outside.y);
    float aa = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / aa, 0.0, 1.0);
    vec4 textureColor = texture(Sampler0, vec2(texCoord.x, 1.0 - texCoord.y));
    float gradientAlpha = alpha * (1.0 - texCoord.y / controlPercent) * coverage;
    if (textureColor.a <= 0.0 || gradientAlpha <= 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, gradientAlpha);
}
