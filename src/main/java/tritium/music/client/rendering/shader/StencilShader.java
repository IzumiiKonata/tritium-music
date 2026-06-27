package tritium.music.client.rendering.shader;

/**
 * Composites a base texture against a gradient-mask texture. In the original
 * client this produced the karaoke word-by-word lyric wipe. On the deferred
 * Blaze3D pipeline that effect is reimplemented with scissor clipping where it
 * is used, so this composite is a no-op kept for API compatibility.
 */
public class StencilShader {

    public void draw(int baseTexture, int stencilTexture, double x, double y) {
    }

    public void draw(int baseTexture, int stencilTexture, double x, double y, double width, double height) {
    }

    public void draw(int baseTexture, int stencilTexture, double x, double y, double width, double height, double uMax, double vMax) {
    }
}
