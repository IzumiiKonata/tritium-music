package tritium.music.client.rendering.font;

public record ShapedGlyph(int fontSlot, int glyphId, int cluster,
                          float xAdvance, float yAdvance, float xOffset, float yOffset) {
}
