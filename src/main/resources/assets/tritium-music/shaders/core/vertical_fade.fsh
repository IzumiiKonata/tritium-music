#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in float controlPercent;
in float alpha;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, vec2(texCoord.x, 1.0 - texCoord.y));
    if (textureColor.a == 0.0) {
        discard;
    }
    float gradientAlpha = alpha * (1.0 - texCoord.y / controlPercent);
    if (gradientAlpha < 0.0) {
        discard;
    }
    fragColor = vec4(textureColor.rgb, gradientAlpha);
}
