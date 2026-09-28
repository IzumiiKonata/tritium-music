package tritium.music.client.rendering.font;

public final class CharMetrics {

    private final String text;
    private final ShapedGlyph[] glyphs;
    private final int[] glyphCharIndex;
    private final float[] glyphX;
    private final float[] charX;
    private final float[] charAdvance;
    private final float width;
    private final long generation;

    CharMetrics(String text, ShapedGlyph[] glyphs, int[] glyphCharIndex, float[] glyphX,
                float[] charX, float[] charAdvance, float width, long generation) {
        this.text = text;
        this.glyphs = glyphs;
        this.glyphCharIndex = glyphCharIndex;
        this.glyphX = glyphX;
        this.charX = charX;
        this.charAdvance = charAdvance;
        this.width = width;
        this.generation = generation;
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public boolean isEmpty() {
        return glyphs.length == 0;
    }

    public float width() {
        return width;
    }

    public long generation() {
        return generation;
    }

    public int glyphCount() {
        return glyphs.length;
    }

    public ShapedGlyph glyph(int index) {
        return glyphs[index];
    }

    public int charIndexOfGlyph(int index) {
        return glyphCharIndex[index];
    }

    public float glyphX(int index) {
        return glyphX[index];
    }

    public float xOfChar(int charIndex) {
        if (charIndex <= 0) {
            return charX[0];
        }
        if (charIndex >= charX.length) {
            return width;
        }
        return charX[charIndex];
    }

    public float advanceOfChar(int charIndex) {
        if (charIndex < 0 || charIndex >= charAdvance.length) {
            return 0f;
        }
        return charAdvance[charIndex];
    }

    public float endOfChar(int charIndex) {
        return xOfChar(charIndex) + advanceOfChar(charIndex);
    }
}
