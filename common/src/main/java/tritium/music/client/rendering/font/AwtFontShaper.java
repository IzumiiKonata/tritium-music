package tritium.music.client.rendering.font;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AwtFontShaper extends FontShaper {

    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final int MAX_SIZED_FONTS = 16;

    private final Font base;
    private final String description;
    private final Map<Integer, Font> sized = new ConcurrentHashMap<>();

    private volatile boolean disposed;

    AwtFontShaper(Font base, String description) {
        this.base = base;
        this.description = description == null ? "" : description;
    }

    @Override
    public String backendName() {
        return "awt";
    }

    @Override
    public boolean isUsable() {
        return !disposed && base != null;
    }

    @Override
    public int glyphCount() {
        if (base == null) {
            return 1;
        }
        try {
            return Math.max(1, base.getNumGlyphs());
        } catch (Throwable throwable) {
            return 1;
        }
    }

    @Override
    public int faceIndex() {
        return 0;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return isUsable() && base.canDisplay(codePoint);
    }

    @Override
    public int nominalGlyph(int codePoint) {
        if (!isUsable() || !base.canDisplay(codePoint)) {
            return -1;
        }
        try {
            GlyphVector vector = base.createGlyphVector(CONTEXT, new String(Character.toChars(codePoint)));
            return vector.getNumGlyphs() == 0 ? -1 : vector.getGlyphCode(0);
        } catch (Throwable throwable) {
            return -1;
        }
    }

    @Override
    public boolean matchesGlyphIds(Font awtFont) {
        return awtFont != null;
    }

    @Override
    public List<ShapedGlyph> shape(CharSequence text, int start, int end, float pixelSize,
                                   int fontSlot, boolean discretionaryLigatures) {
        List<ShapedGlyph> result = new ArrayList<>();
        if (!isUsable() || end <= start || end > text.length()) {
            return result;
        }

        Font font = fontAt(pixelSize);
        if (font == null) {
            return result;
        }

        try {
            GlyphVector vector = font.createGlyphVector(CONTEXT, text.subSequence(start, end).toString());
            int count = vector.getNumGlyphs();
            if (count <= 0) {
                return result;
            }

            float[] positions = vector.getGlyphPositions(0, count, null);
            int[] codes = vector.getGlyphCodes(0, count, null);
            int[] indices = vector.getGlyphCharIndices(0, count, null);
            if (positions == null || codes == null) {
                return result;
            }

            float penX = 0f;
            float penY = 0f;
            int fallbackCluster = 0;

            for (int i = 0; i < count; i++) {
                float x = positions[i * 2];
                float y = positions[i * 2 + 1];
                float nextX = i + 1 < count ? positions[i * 2 + 2] : x + advanceOf(vector.getGlyphMetrics(i));
                float nextY = i + 1 < count ? positions[i * 2 + 3] : y;

                int cluster = indices != null && indices[i] >= 0 ? indices[i] : fallbackCluster;
                fallbackCluster = cluster + 1;

                result.add(new ShapedGlyph(fontSlot, codes[i], start + cluster,
                        nextX - x, nextY - y, x - penX, y - penY));

                penX += nextX - x;
                penY += nextY - y;
            }
        } catch (Throwable throwable) {
            result.clear();
        }

        return result;
    }

    private static float advanceOf(GlyphMetrics metrics) {
        if (metrics == null) {
            return 0f;
        }
        float advance = metrics.getAdvanceX();
        return advance != 0f ? advance : (float) metrics.getAdvance();
    }

    private Font fontAt(float pixelSize) {
        int size = Math.max(1, Math.round(pixelSize));
        Font cached = sized.get(size);
        if (cached != null) {
            return cached;
        }
        Font derived;
        try {
            derived = base.deriveFont((float) size);
        } catch (Throwable throwable) {
            return base;
        }
        if (derived == null) {
            return base;
        }
        if (sized.size() >= MAX_SIZED_FONTS) {
            sized.clear();
        }
        sized.put(size, derived);
        return derived;
    }

    @Override
    public void dispose() {
        disposed = true;
        sized.clear();
    }
}
