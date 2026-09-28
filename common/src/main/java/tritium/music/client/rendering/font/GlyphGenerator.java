package tritium.music.client.rendering.font;

import tritium.music.core.util.AsyncUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class GlyphGenerator {

    static final FontRenderContext context = new FontRenderContext(null, true, true);

    private static final int BITMAP_PADDING = 1;
    private static final double ITALIC_SHEAR = 0.22;

    public interface GlyphLoadedCallback {
        void onLoaded(double fontHeight);
    }

    interface GlyphStore extends java.util.function.Consumer<Glyph> {
    }

    static int lineHeightPx(Font font) {
        if (font == null) {
            return 0;
        }
        LineMetrics metrics = font.getLineMetrics("Ag", context);
        return (int) Math.ceil(metrics.getAscent() + metrics.getDescent());
    }

    static Font fontForGlyph(int codePoint, Font primary, Font[] fallbacks) {
        if (primary != null && primary.canDisplay(codePoint)) {
            return primary;
        }
        if (fallbacks != null) {
            for (Font fallback : fallbacks) {
                if (fallback != null && fallback.canDisplay(codePoint)) {
                    return fallback;
                }
            }
        }
        return primary;
    }

    public static void generateChar(CFontRenderer fr, char ch, Font font, Font[] fallbacks, int bandHeight,
                                    TextureAtlas atlas, long generation, GlyphLoadedCallback onLoaded) {
        if (font == null || atlas == null || atlas.isDestroyed()) {
            return;
        }

        Font target = fontForGlyph(ch, font, fallbacks);
        if (target == null) {
            return;
        }

        GlyphVector gv = target.createGlyphVector(context, String.valueOf(ch));
        generate(fr, target, gv, bandHeight, atlas, generation, onLoaded,
                glyph -> fr.allGlyphs[ch] = glyph,
                glyph -> fr.discardGlyph(ch, glyph));
    }

    public static void generateById(CFontRenderer fr, int slot, Font font, int glyphId, int bandHeight,
                                    TextureAtlas atlas, long generation, GlyphLoadedCallback onLoaded) {
        if (font == null || atlas == null || atlas.isDestroyed()) {
            return;
        }

        GlyphVector gv;
        try {
            gv = font.createGlyphVector(context, new int[]{glyphId});
        } catch (Throwable throwable) {
            fr.storeShapedGlyph(slot, glyphId, blank(1, bandHeight));
            return;
        }
        if (gv == null || gv.getNumGlyphs() == 0) {
            fr.storeShapedGlyph(slot, glyphId, blank(1, bandHeight));
            return;
        }

        generate(fr, font, gv, bandHeight, atlas, generation, onLoaded,
                glyph -> fr.storeShapedGlyph(slot, glyphId, glyph),
                glyph -> fr.discardShapedGlyph(slot, glyphId, glyph));
    }

    private static Glyph blank(int width, int height) {
        Glyph glyph = new Glyph(0, Math.max(1, height), Math.max(1, width), Math.max(1, height), 0, 0, (char) 0);
        glyph.uploaded = true;
        return glyph;
    }

    private static void generate(CFontRenderer fr, Font font, GlyphVector gv, int bandHeight,
                                 TextureAtlas atlas, long generation, GlyphLoadedCallback onLoaded,
                                 GlyphStore store, GlyphStore discard) {
        try {
            generateUnsafe(fr, font, gv, bandHeight, atlas, generation, onLoaded, store, discard);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            store.accept(blank(1, bandHeight));
        }
    }

    private static void generateUnsafe(CFontRenderer fr, Font font, GlyphVector gv, int bandHeight,
                                       TextureAtlas atlas, long generation, GlyphLoadedCallback onLoaded,
                                       GlyphStore store, GlyphStore discard) {
        LineMetrics metrics = font.getLineMetrics("Ag", context);

        int advance = (int) Math.ceil(gv.getGlyphMetrics(0).getAdvance());
        int height = Math.max(1, bandHeight);

        Rectangle2D visual = gv.getVisualBounds();
        boolean hasInk = !visual.isEmpty();
        double visualMinX = hasInk ? visual.getMinX() : 0;
        double visualMaxX = hasInk ? visual.getMaxX() : advance;

        boolean slanted = (font.getStyle() & Font.ITALIC) != 0;
        int shearExtent = slanted ? (int) Math.ceil(Math.max(0f, metrics.getAscent()) * ITALIC_SHEAR) + 1 : 0;

        int right = Math.max(advance, (int) Math.ceil(visualMaxX));
        int overhang = Math.max(0, right - advance);
        right = Math.max(right, advance + shearExtent) + BITMAP_PADDING;
        int left = Math.min(0, (int) Math.floor(visualMinX)) - BITMAP_PADDING;

        int bitmapWidth = Math.max(1, right - left);
        int bitmapHeight = height;

        Glyph glyph = new Glyph(advance, height, bitmapWidth, bitmapHeight, left, overhang, (char) 0);
        store.accept(glyph);

        if (!hasInk) {
            glyph.uploaded = true;
            return;
        }

        AsyncUtil.runAsync(() -> {
            if (fr.getLayoutGeneration() != generation || atlas.isDestroyed()) {
                return;
            }

            BufferedImage bi = null;
            try {
                bi = new BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = bi.createGraphics();
                g2d.setColor(new Color(255, 255, 255, 255));
                g2d.setComposite(AlphaComposite.Src);
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
                g2d.setFont(font);
                int baselineY = Math.round((height + metrics.getAscent() - metrics.getDescent()) * 0.5f);
                g2d.drawGlyphVector(gv, -left, baselineY);
                g2d.dispose();

                for (int x = 0; x < bi.getWidth(); x++) {
                    for (int y = 0; y < bi.getHeight(); y++) {
                        int alpha = bi.getRGB(x, y) >>> 24;
                        bi.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
                    }
                }

                if (fr.getLayoutGeneration() != generation || atlas.isDestroyed()) {
                    bi.flush();
                    return;
                }

                onLoaded.onLoaded(height);
                BufferedImage image = bi;
                AsyncUtil.runOnRenderThread(() -> {
                    try {
                        if (fr.getLayoutGeneration() != generation || atlas.isDestroyed()) {
                            return;
                        }
                        TextureAtlas.AtlasRegion region = atlas.upload(image);
                        if (region == null) {
                            discard.accept(glyph);
                        } else {
                            glyph.setAtlasRegion(region);
                            atlas.scheduleFlush();
                        }
                    } finally {
                        image.flush();
                    }
                });
            } catch (Throwable throwable) {
                if (bi != null) {
                    bi.flush();
                }
                discard.accept(glyph);
            }
        });
    }
}
