package tritium.music.client.rendering;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import tritium.music.client.rendering.font.Glyph;

import java.util.List;

public final class LyricOffscreen {

    private LyricOffscreen() {
    }

    public static void renderStencilMask(TRenderTarget rt, int w, int h,
                                         double sungW, double gradW) {
        NativeImage img = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a;
                if (x < sungW - gradW) {
                    a = 255;
                } else if (x < sungW) {
                    double t = (x - (sungW - gradW)) / gradW;
                    a = (int) ((1.0 - t) * 255.0);
                } else {
                    a = 0;
                }
                img.setPixelABGR(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        encoder.writeToTexture(rt.colorTexture(), img);
        encoder.submit();
        img.close();
    }

    public static void renderBaseGlyphs(TRenderTarget rt, int w, int h,
                                        Glyph[] glyphTable, int baseColor,
                                        List<GlyphCmd> glyphs, int blitScale) {
        int colorA = (baseColor >> 24) & 0xFF;
        float colorAf = colorA / 255f;

        NativeImage img = new NativeImage(w, h, true);

        for (GlyphCmd cmd : glyphs) {
            Glyph g = glyphTable[cmd.ch];
            if (g == null || !g.uploaded || g.atlasImage == null) continue;

            NativeImage atlas = g.atlasImage;
            int srcX = Math.round(g.u0 * atlas.getWidth());
            int srcY = Math.round(g.v0 * atlas.getHeight());
            int srcW = g.width;
            int srcH = g.height;

            int left = Math.max(0, (int) Math.floor(cmd.x - 1));
            int top = Math.max(0, (int) Math.floor(cmd.y - 1));
            int right = Math.min(w, (int) Math.ceil(cmd.x + srcW * blitScale + 1));
            int bottom = Math.min(h, (int) Math.ceil(cmd.y + srcH * blitScale + 1));

            for (int dy = top; dy < bottom; dy++) {
                float sampleY = ((dy + .5f) - cmd.y) / blitScale - .5f;
                for (int dx = left; dx < right; dx++) {
                    float sampleX = ((dx + .5f) - cmd.x) / blitScale - .5f;
                    float glyphA = sampleAlpha(atlas, srcX, srcY, srcW, srcH, sampleX, sampleY);
                    int outA = Math.clamp(Math.round(glyphA * colorAf), 0, 255);
                    if (outA != 0) {
                        img.setPixelABGR(dx, dy, (outA << 24) | 0x00FFFFFF);
                    }
                }
            }
        }

        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        encoder.writeToTexture(rt.colorTexture(), img);
        encoder.submit();
        img.close();
    }

    private static float sampleAlpha(NativeImage image, int originX, int originY, int width, int height,
                                     float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float tx = x - x0;
        float ty = y - y0;
        float top = alpha(image, originX, originY, width, height, x0, y0) * (1 - tx)
                + alpha(image, originX, originY, width, height, x1, y0) * tx;
        float bottom = alpha(image, originX, originY, width, height, x0, y1) * (1 - tx)
                + alpha(image, originX, originY, width, height, x1, y1) * tx;
        return top * (1 - ty) + bottom * ty;
    }

    private static int alpha(NativeImage image, int originX, int originY, int width, int height, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return image.getPixel(originX + x, originY + y) >>> 24;
    }

    public static final class GlyphCmd {
        public final char ch;
        public final float x;
        public final float y;

        public GlyphCmd(char ch, float x, float y) {
            this.ch = ch;
            this.x = x;
            this.y = y;
        }
    }
}
