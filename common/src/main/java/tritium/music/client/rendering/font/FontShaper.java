package tritium.music.client.rendering.font;

import java.awt.Font;
import java.util.List;

public abstract class FontShaper {

    public static volatile boolean discretionaryLigatures = false;

    public static FontShaper create(Font awtFont, byte[] data, String postScriptName, String description) {
        if (awtFont == null) {
            return null;
        }

        if (HarfBuzzSupport.isAvailable()) {
            try {
                FontShaper shaper = new HarfBuzzFontShaper(data, postScriptName, description);
                if (shaper.isUsable() && shaper.matchesGlyphIds(awtFont)) {
                    return shaper;
                }
                shaper.dispose();
            } catch (Throwable throwable) {
                HarfBuzzSupport.markUnavailable(throwable);
            }
        }

        return new AwtFontShaper(awtFont, description);
    }

    public abstract String backendName();

    public abstract boolean isUsable();

    public abstract int glyphCount();

    public abstract int faceIndex();

    public abstract String description();

    public abstract boolean hasGlyph(int codePoint);

    public abstract int nominalGlyph(int codePoint);

    public abstract boolean matchesGlyphIds(Font awtFont);

    public abstract List<ShapedGlyph> shape(CharSequence text, int start, int end, float pixelSize,
                                            int fontSlot, boolean discretionaryLigatures);

    public abstract void dispose();
}
