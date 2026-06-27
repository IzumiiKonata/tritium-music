package tritium.music.client.rendering.font;

import tritium.music.core.util.AsyncUtil;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class GlyphGenerator {

    static final AffineTransform transformation = new AffineTransform();
    static final FontRenderContext context = new FontRenderContext(transformation, true, true);

    private static boolean canFontDisplayChar(Font f, char ch) {
        return f.canDisplay(ch);
    }

    private static Font getFontForGlyph(char ch, Font f, Font... fallBackFonts) {
        if (!canFontDisplayChar(f, ch)) {
            if (fallBackFonts != null) {
                for (Font fallBackFont : fallBackFonts) {
                    if (fallBackFont != null && canFontDisplayChar(fallBackFont, ch)) {
                        return fallBackFont;
                    }
                }
            }
        }
        return f;
    }

    private static int getMaxFontHeight(Font originalFont, Font[] fallbackFonts) {
        int maxHeight = fontGraphics.getFontMetrics(originalFont).getAscent() +
                fontGraphics.getFontMetrics(originalFont).getDescent();

        if (fallbackFonts != null) {
            for (Font fallbackFont : fallbackFonts) {
                if (fallbackFont != null) {
                    FontMetrics fm = fontGraphics.getFontMetrics(fallbackFont);
                    int height = fm.getAscent() + fm.getDescent();
                    maxHeight = Math.max(maxHeight, height);
                }
            }
        }

        return maxHeight;
    }

    static final BufferedImage fontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    static final Graphics2D fontGraphics = (Graphics2D) fontImage.getGraphics();

    public static void generate(CFontRenderer fr, char ch, Font originalFont,
                                TextureAtlas atlas, GlyphLoadedCallback onLoaded) {
        Font fallbackFont = getFontForGlyph(ch, originalFont, fr.fallBackFonts);

        final FontMetrics fontMetrics = fontGraphics.getFontMetrics(fallbackFont);

        transformation.setToIdentity();
        GlyphVector gv = fallbackFont.createGlyphVector(context, String.valueOf(ch));
        int width = (int) Math.ceil(gv.getGlyphMetrics(0).getAdvance());
        int height = getMaxFontHeight(originalFont, fr.fallBackFonts);

        Glyph glyph = new Glyph(width, height, ch);
        fr.allGlyphs[ch] = glyph;

        if (width == 0) {
            glyph.uploaded = true;
            return;
        }

        AsyncUtil.runAsync(() -> {
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = bi.createGraphics();
            g2d.setColor(new Color(255, 255, 255, 255));
            g2d.setComposite(AlphaComposite.Src);

            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);

            g2d.setFont(fallbackFont);

            int baselineY = (height + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;
            g2d.drawString(String.valueOf(ch), 0, baselineY);

            g2d.dispose();

            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    int rgb = bi.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    bi.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
                }
            }

            onLoaded.onLoaded(height);

            AsyncUtil.runOnRenderThread(() -> {
                TextureAtlas.AtlasRegion region = atlas.upload(bi);
                if (region != null) {
                    glyph.setAtlasRegion(region);
                }
                bi.flush();
            });
        });
    }

    public interface GlyphLoadedCallback {
        void onLoaded(double fontHeight);
    }
}
