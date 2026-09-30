#version 150

uniform sampler2D Sampler0;
uniform vec4 CompositeRect;
uniform float Opacity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (gl_FragCoord.x < CompositeRect.x || gl_FragCoord.x > CompositeRect.z
            || gl_FragCoord.y < CompositeRect.y || gl_FragCoord.y > CompositeRect.w) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord);
    fragColor = vec4(color.rgb, Opacity);
}
