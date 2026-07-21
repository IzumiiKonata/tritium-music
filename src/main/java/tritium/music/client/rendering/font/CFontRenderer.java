package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import lombok.SneakyThrows;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.util.Mth;

import java.awt.Color;
import java.awt.Font;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CFontRenderer implements Closeable {

    public Glyph[] allGlyphs = new Glyph['￿' + 1];

    public Font font;
    public Font[] fallBackFonts;
    public float sizePx;
    private final TextureAtlas atlas;
    private FontKerning fontKerning;
    private final Object glyphLock = new Object();

    public CFontRenderer(Font font, float sizePx) {
        this.sizePx = sizePx;
        this.atlas = new TextureAtlas();
        init(font, sizePx);
    }

    @SneakyThrows
    public CFontRenderer(Font font, float sizePx, Font... fallBackFonts) {
        this(font, sizePx);

        this.fallBackFonts = new Font[fallBackFonts.length];

        for (int i = 0; i < fallBackFonts.length; i++) {
            this.fallBackFonts[i] = fallBackFonts[i].deriveFont(sizePx * 2);
        }
    }

    public CFontRenderer(Font font, float sizePx, FontKerning fontKerning, Font... fallBackFonts) {
        this(font, sizePx, fallBackFonts);
        this.fontKerning = fontKerning;
    }

    public static String stripControlCodes(String text) {
        char[] chars = text.toCharArray();
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '§') {
                i++;
                continue;
            }
            f.append(c);
        }
        return f.toString();
    }

    private void init(Font font, float sizePx) {
        this.font = font.deriveFont(sizePx * 2);
    }

    public double fontHeight = -1;
    final Object fontHeightLock = new Object();

    private Glyph locateGlyph(char ch) {
        synchronized (glyphLock) {
            Glyph glyph = allGlyphs[ch];
            if (glyph != null && glyph.uploaded) {
                return glyph;
            }
            if (glyph == null) {
                GlyphGenerator.generate(this, ch, this.font, atlas, fontHeight -> {
                    synchronized (fontHeightLock) {
                        this.fontHeight = Math.max(this.fontHeight, fontHeight);
                    }
                });
            }
            return null;
        }
    }

    void discardGlyph(char ch, Glyph glyph) {
        synchronized (glyphLock) {
            if (allGlyphs[ch] == glyph && !glyph.uploaded) {
                allGlyphs[ch] = null;
            }
        }
    }

    public void prewarm(String text) {
        if (text == null) return;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') continue;
            if (c == '\247' && i + 1 < text.length()) { i++; continue; }
            if (c == '（') c = '(';
            if (c == '）') c = ')';
            if (c == '・') c = '·';
            locateGlyph(c);
        }
    }

    public float drawString(String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float a = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;
        drawString(s, x, y, r, g, b, a);
        return (float) getStringWidthD(s);
    }

    public float drawCharGradient(char c, double x, double y, int color, float leftAlphaMul, float rightAlphaMul) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float baseA = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;

        GuiGraphicsExtractor graphics = RenderContext.graphics();
        Matrix3x2fStack pose = graphics.pose();

        y -= 2.0f;

        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(0.5f, 0.5f);

        Glyph glyph = locateGlyph(c);
        if (glyph != null && glyph.uploaded) {
            int leftColor = packColor(r, g, b, baseA * leftAlphaMul);
            int rightColor = packColor(r, g, b, baseA * rightAlphaMul);
            Render.glyph(graphics, glyph.atlasIdentifier, 0, 0, glyph.width, glyph.height,
                    glyph.u0, glyph.v0, glyph.u1, glyph.v1, leftColor, rightColor);
            pose.popMatrix();
            return glyph.width * 0.5f;
        }

        pose.popMatrix();
        return 0f;
    }

    public int drawStringWithShadow(String text, double x, double y, int color) {
        int a = (color >> 24) & 0xff;
        drawString(stripControlCodes(text), x + 1, y + 1, RGBA.color(0, 0, 0, a));
        drawString(text, x, y, color);
        return this.getStringWidth(text);
    }

    public void drawString(String s, double x, double y, Color color) {
        drawString(s, x, y, color.getRed() * RGBA.DIVIDE_BY_255, color.getGreen() * RGBA.DIVIDE_BY_255, color.getBlue() * RGBA.DIVIDE_BY_255, color.getAlpha() * RGBA.DIVIDE_BY_255);
    }

    public void drawCenteredStringVertical(String text, double x, double y, int color) {
        drawString(text, x, y - this.getFontHeight() * .5, color);
    }

    private int getColorCode(char c) {
        return switch (c) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> Integer.MIN_VALUE;
        };
    }

    public boolean drawString(String s, double x, double y, float r, float g, float b, float a) {
        GuiGraphicsExtractor graphics = RenderContext.graphics();
        Matrix3x2fStack pose = graphics.pose();

        y -= 2.0f;

        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(0.5f, 0.5f);

        boolean allLoaded = drawStringImmediateMode(s, r, g, b, a);

        pose.popMatrix();
        return allLoaded;
    }

    private boolean drawStringImmediateMode(String s, float r, float g, float b, float a) {
        boolean allLoaded = true;
        double xOffset = 0;
        double yOffset = 0;
        boolean inSel = false;
        List<GlyphBatch> batches = new ArrayList<>();

        int curColor = packColor(r, g, b, a);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char nextChar = i + 1 < s.length() ? s.charAt(i + 1) : '\0';

            if (inSel) {
                inSel = false;
                if (c == 'r') {
                    curColor = packColor(r, g, b, a);
                } else {
                    int colorCode = this.getColorCode(c);
                    if (colorCode != Integer.MIN_VALUE) {
                        int red = colorCode >> 16 & 0xFF;
                        int green = colorCode >> 8 & 0xFF;
                        int blue = colorCode & 0xFF;
                        curColor = RGBA.color(red, green, blue, (int) (a * 255));
                    }
                }
                continue;
            }

            if (c == '§') {
                inSel = true;
                continue;
            }
            if (c == '\n') {
                yOffset += this.getHeight() * 2 + 4;
                xOffset = 0;
                continue;
            }

            if (c == '（') c = '(';
            if (c == '）') c = ')';
            if (c == '・') c = '·';

            Glyph glyph = locateGlyph(c);
            if (glyph != null && glyph.uploaded) {
                float x0 = (float) xOffset;
                float y0 = (float) yOffset;
                addGlyph(batches, glyph, x0, y0, curColor, curColor);

                xOffset += glyph.width;

                if (fontKerning != null && nextChar != '\0' && nextChar != '§' && nextChar != '\n') {
                    xOffset += fontKerning.getKerning(c, nextChar, sizePx) * 2;
                }
            } else {
                allLoaded = false;
            }
        }

        drawBatches(batches);
        return allLoaded;
    }

    private static void addGlyph(List<GlyphBatch> batches, Glyph glyph, float x, float y,
                                 int leftColor, int rightColor) {
        GlyphBatch batch;
        if (batches.isEmpty() || !batches.getLast().atlas.equals(glyph.atlasIdentifier)) {
            batch = new GlyphBatch(glyph.atlasIdentifier);
            batches.add(batch);
        } else {
            batch = batches.getLast();
        }
        batch.quads.add(new Render.GlyphQuad(x, y, glyph.width, glyph.height,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1, leftColor, rightColor));
    }

    private static void drawBatches(List<GlyphBatch> batches) {
        for (GlyphBatch batch : batches) {
            Render.glyphs(RenderContext.graphics(), batch.atlas, batch.quads);
        }
    }

    private static final class GlyphBatch {
        private final Identifier atlas;
        private final List<Render.GlyphQuad> quads = new ArrayList<>();

        private GlyphBatch(Identifier atlas) {
            this.atlas = atlas;
        }
    }

    private static int packColor(float r, float g, float b, float a) {
        return RGBA.color((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
    }

    public void drawCenteredString(String s, double x, double y, int color) {
        _drawCenteredString(s, x, y, color);
    }

    public boolean _drawCenteredString(String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float a = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;

        return drawString(s, (x - getStringWidthD(s) * .5), y, r, g, b, a);
    }

    public void drawCenteredStringWithShadow(String s, double x, double y, int color) {
        drawStringWithShadow(s, (x - getStringWidthD(s) * .5), y, color);
    }

    public void drawCenteredStringMultiLine(String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float a = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;

        double offsetY = y;
        for (String string : s.split("\n")) {
            drawString(string, (x - getStringWidthD(string) / 2.0), offsetY, r, g, b, a);
            offsetY += this.getFontHeight();
        }
    }

    public String trim(String text, double width) {
        String name = text;

        if (this.getStringWidthD(name) > width) {
            int idx = name.length() - 1;
            while (idx > 0) {
                String substring = name.substring(0, idx);

                if (this.getStringWidthD(substring + "...") <= width) {
                    name = substring + "...";
                    break;
                }

                idx--;
            }
        }

        return name;
    }

    public int getStringWidth(String text) {
        return Mth.floor(getStringWidthD(text));
    }

    private final Map<String, Double> stringWidthMapD = new HashMap<>();

    public boolean areGlyphsLoaded(String text) {
        for (char c : text.toCharArray()) {
            if (c == '（') c = '(';
            if (c == '）') c = ')';
            if (c == '\n') continue;
            if (c == ' ') continue;
            if (c == '\r') continue;
            if (c == '\t') continue;
            if (c == '\247') continue;

            Glyph gly = allGlyphs[c];
            if (gly == null || !gly.uploaded) {
                locateGlyph(c);
                return false;
            }
        }

        return true;
    }

    public double getStringWidthD(String text) {
        Double f = this.stringWidthMapD.get(text);
        if (f != null)
            return f;

        boolean shouldntAdd = false;

        char[] c = stripControlCodes(text).toCharArray();
        double currentLine = 0;
        double maxPreviousLines = 0;
        for (int i = 0; i < c.length; i++) {
            char c1 = c[i];
            char c2 = i + 1 < c.length ? c[i + 1] : '\0';

            if (c1 == '\n') {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }

            if (c1 == '（') c1 = '(';
            if (c1 == '）') c1 = ')';

            float charWidth = getCharWidth(c1, c2);

            if (!shouldntAdd) {
                shouldntAdd = charWidth == 0;
            }

            currentLine += charWidth;
        }

        if (!shouldntAdd) {
            this.stringWidthMapD.put(text, Math.max(currentLine, maxPreviousLines));
        }

        return Math.max(currentLine, maxPreviousLines);
    }

    public int getHeight() {
        return (int) this.getFontHeight();
    }

    public net.minecraft.resources.Identifier getAtlasId() {
        return atlas.identifier();
    }

    public Glyph[] getAllGlyphs() {
        return allGlyphs;
    }

    public NativeImage getAtlasImage() {
        return atlas.getImage();
    }

    public double getFontHeight() {
        return (this.fontHeight - 8.5) * .5;
    }

    @Override
    public void close() {
        atlas.destroy();
        atlas.init();
        allGlyphs = new Glyph['￿' + 1];
        stringWidthMapD.clear();
        fontHeight = -1;
    }

    float getCharWidth(char ch) {
        return getCharWidth(ch, '\0');
    }

    public float getCharWidth(char ch, char nextChar) {
        if (ch == '（') ch = '(';
        if (ch == '）') ch = ')';
        if (ch == '・') ch = '·';

        Glyph glyph = allGlyphs[ch];

        if (glyph == null) {
            locateGlyph(ch);
            return .0f;
        }

        float width = glyph.width * .5f;

        if (fontKerning != null && nextChar != '\0') {
            width += fontKerning.getKerning(ch, nextChar, sizePx);
        }

        return width;
    }

    public double getStringHeight(String text) {
        return text.split("\n").length * (getFontHeight() + 4) - 4;
    }

    public String[] fitWidth(String text, double width) {
        List<String> lines = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            int previousI = i;
            LineBreakResult result = findLineBreak(text, i, width);

            lines.add(text.substring(i, result.endIndex));

            i = result.nextStartIndex;

            if (i == previousI) {
                i++;
            }
        }

        return lines.toArray(new String[0]);
    }

    static final char[] breakableChars = new char['￿' + 1];
    static String breakable = " .。,，!！?？;；、";
    static String wrapStarts = "(（「『{[【<";
    static String wrapEnds = ")）」』}]】>";

    static {
        for (char c : breakable.toCharArray()) {
            breakableChars[c] = 2;
        }
        for (char c : wrapStarts.toCharArray()) {
            breakableChars[c] = 3;
        }
        for (char c : wrapEnds.toCharArray()) {
            breakableChars[c] = 1;
        }
    }

    private int findMatchingOpenBracket(String text, int closeIndex, int startIndex) {
        char closeChar = text.charAt(closeIndex);
        int closeCharType = wrapEnds.indexOf(closeChar);
        if (closeCharType == -1) {
            return -1;
        }

        char openChar = wrapStarts.charAt(closeCharType);
        int depth = 1;

        for (int i = closeIndex - 1; i >= startIndex; i--) {
            char c = text.charAt(i);

            if (i > startIndex && text.charAt(i - 1) == '\247') {
                i--;
                continue;
            }

            if (c == closeChar) {
                depth++;
            } else if (c == openChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private LineBreakResult findLineBreak(String text, int startIndex, double maxWidth) {
        double currentWidth = 0;
        int lastBreakableIndex = -1;
        int lastBreakableIndexPriority = 0;
        boolean lastBreakableTrimThisChar = false;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            char nextChar = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (c == '\247' && i + 1 < text.length()) {
                i++;
                continue;
            }

            if (c == '\n') {
                return new LineBreakResult(i, i + 1);
            }

            int breakableCharValue = breakableChars[c];
            if (breakableCharValue > 0) {
                if (breakableCharValue == 1) {
                    int openIndex = findMatchingOpenBracket(text, i, startIndex);
                    if (openIndex != -1 && openIndex == lastBreakableIndex) {
                        lastBreakableIndexPriority = 2;
                        lastBreakableIndex = i;
                        lastBreakableTrimThisChar = false;
                    } else if (breakableCharValue >= lastBreakableIndexPriority) {
                        lastBreakableIndexPriority = breakableCharValue;
                        lastBreakableIndex = i;
                    }
                } else if (breakableCharValue >= lastBreakableIndexPriority) {
                    lastBreakableIndexPriority = breakableCharValue;
                    lastBreakableIndex = i;
                    lastBreakableTrimThisChar = (c == ' ');
                }
            }

            double charWidth = getCharWidth(c, nextChar);

            if (currentWidth + charWidth > maxWidth) {
                if (breakableCharValue > 0 && breakableCharValue >= lastBreakableIndexPriority) {
                    return handleBreakAtCurrentChar(i, breakableCharValue, lastBreakableTrimThisChar);
                }

                if (lastBreakableIndex != -1) {
                    return handleBreakAtLastBreakable(text, lastBreakableIndex, lastBreakableTrimThisChar, startIndex, i);
                }

                if (i == startIndex) {
                    return new LineBreakResult(startIndex + 1, startIndex + 1);
                }
                return new LineBreakResult(i, i);
            }

            currentWidth += charWidth;
        }

        return new LineBreakResult(text.length(), text.length());
    }

    private LineBreakResult handleBreakAtCurrentChar(int index, int breakableCharValue, boolean trimThisChar) {
        if (trimThisChar) {
            return new LineBreakResult(index, index + 1);
        }

        if (breakableCharValue == 3) {
            return new LineBreakResult(index, index);
        }

        return new LineBreakResult(index + 1, index + 1);
    }

    private LineBreakResult handleBreakAtLastBreakable(String text, int lastBreakableIndex,
                                                       boolean trimThisChar, int startIndex, int currentIndex) {
        if (trimThisChar) {
            return new LineBreakResult(lastBreakableIndex, lastBreakableIndex + 1);
        }

        char lastBreakableChar = text.charAt(lastBreakableIndex);
        int lastBreakableCharValue = breakableChars[lastBreakableChar];

        if (lastBreakableCharValue == 3) {
            if (lastBreakableIndex == startIndex) {
                if (currentIndex == startIndex) {
                    return new LineBreakResult(startIndex + 1, startIndex + 1);
                }
                return new LineBreakResult(currentIndex, currentIndex);
            }
            return new LineBreakResult(lastBreakableIndex, lastBreakableIndex);
        }

        return new LineBreakResult(lastBreakableIndex + 1, lastBreakableIndex + 1);
    }

    private static class LineBreakResult {
        final int endIndex;
        final int nextStartIndex;

        LineBreakResult(int endIndex, int nextStartIndex) {
            this.endIndex = endIndex;
            this.nextStartIndex = nextStartIndex;
        }
    }

    public void drawStringWithBetterShadow(String text, double x, double y, int color) {
        drawString(text, x, y, color);
    }

    public void drawOutlineCenteredString(String text, double x, double y, int color, int outlineColor) {
        drawOutlineString(text, x - getStringWidthD(text) * .5, y, color, outlineColor);
    }

    public void drawOutlineString(String text, double x, double y, int color, int outlineColor) {
        String outlinetext = stripControlCodes(text);
        drawString(outlinetext, x + 0.5, y, outlineColor);
        drawString(outlinetext, x - 0.5, y, outlineColor);
        drawString(outlinetext, x, y + 0.5, outlineColor);
        drawString(outlinetext, x, y - 0.5, outlineColor);
        drawString(text, x, y, color);
    }

    public int getWidth(String text) {
        return this.getStringWidth(text);
    }
}
