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
                                        NativeImage atlas, int atlasW, int atlasH,
                                        List<GlyphCmd> glyphs, int blitScale) {
        int colorA = (baseColor >> 24) & 0xFF;
        float colorAf = colorA / 255f;

        NativeImage img = new NativeImage(w, h, true);

        for (GlyphCmd cmd : glyphs) {
            Glyph g = glyphTable[cmd.ch];
            if (g == null || !g.uploaded) continue;

            int srcX = Math.round(g.u0 * atlasW);
            int srcY = Math.round(g.v0 * atlasH);
            int srcW = g.width;
            int srcH = g.height;

            int dstX = Math.round(cmd.x);
            int dstY = Math.round(cmd.y);

            for (int py = 0; py < srcH; py++) {
                int sy = srcY + py;
                int dyBase = dstY + py * blitScale;
                if (dyBase + blitScale - 1 < 0 || dyBase >= h) continue;

                for (int px = 0; px < srcW; px++) {
                    int sx = srcX + px;
                    int dxBase = dstX + px * blitScale;
                    if (dxBase + blitScale - 1 < 0 || dxBase >= w) continue;

                    int argb = atlas.getPixel(sx, sy);
                    int glyphA = (argb >> 24) & 0xFF;
                    int outA = (int) (glyphA * colorAf);
                    int outBgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                    int outPixel = (outA << 24) | outBgr;

                    for (int by = 0; by < blitScale; by++) {
                        int dy = dyBase + by;
                        if (dy < 0 || dy >= h) continue;
                        for (int bx = 0; bx < blitScale; bx++) {
                            int dx = dxBase + bx;
                            if (dx < 0 || dx >= w) continue;
                            img.setPixelABGR(dx, dy, outPixel);
                        }
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
