package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.util.Mth;

import java.awt.*;
import java.io.Closeable;
import java.util.*;
import java.util.List;

public class CFontRenderer implements Closeable {

    public record Face(Font font, FontShaper shaper) {
    }

    public static volatile boolean advancedShaping = true;

    private static final double MAX_BAND_RATIO = 1.4;

    public Glyph[] allGlyphs = new Glyph['￿' + 1];

    public Font font;
    public Font[] fallBackFonts;
    public float sizePx;

    private TextureAtlas atlas;
    private final Object glyphLock = new Object();
    private long layoutGeneration;
    private boolean closed;

    private Font[] slotFonts = new Font[1];
    private FontShaper[] shapers = new FontShaper[1];
    private Glyph[][] glyphsBySlot = new Glyph[1][];
    private int lineHeightPx = 1;
    private boolean shapingAvailable;

    private final Map<String, CharMetrics> metricsCache = new LinkedHashMap<>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CharMetrics> eldest) {
            return size() > 512;
        }
    };
    private final Map<Long, Float> kerningCache = new HashMap<>();

    public CFontRenderer(Font font, float sizePx) {
        this(font, sizePx, (FontShaper) null, new Font[0]);
    }

    public CFontRenderer(Font font, float sizePx, FontShaper shaper, Font... fallBackFonts) {
        this.atlas = new TextureAtlas();
        apply(new Face(font, shaper), sizePx, toFaces(fallBackFonts));
    }

    public CFontRenderer(Face primary, float sizePx, Face... fallbacks) {
        this.atlas = new TextureAtlas();
        apply(primary, sizePx, fallbacks);
    }

    private static Face[] toFaces(Font[] fonts) {
        if (fonts == null) {
            return new Face[0];
        }
        Face[] faces = new Face[fonts.length];
        for (int i = 0; i < fonts.length; i++) {
            faces[i] = new Face(fonts[i], null);
        }
        return faces;
    }

    public synchronized void reload(Face primary, float sizePx, Face... fallbacks) {
        synchronized (glyphLock) {
            this.atlas.destroy();
            this.atlas = new TextureAtlas();
            this.closed = false;
            apply(primary, sizePx, fallbacks);
            reset();
        }
    }

    public synchronized void reload(Font font, float sizePx, FontShaper shaper, Font... fallBackFonts) {
        reload(new Face(font, shaper), sizePx, toFaces(fallBackFonts));
    }

    public boolean isClosed() {
        return closed;
    }

    public FontShaper shaper() {
        return shapers.length == 0 ? null : shapers[0];
    }

    public boolean isShapingEnabled() {
        return advancedShaping && shapingAvailable && !closed;
    }

    public boolean hasShaper(int slot) {
        return slot >= 0 && slot < shapers.length && shapers[slot] != null && shapers[slot].isUsable();
    }

    private void apply(Face primary, float sizePx, Face... fallbacks) {
        List<Face> list = new ArrayList<>();
        if (primary != null && primary.font() != null) {
            list.add(primary);
        }
        if (fallbacks != null) {
            for (Face face : fallbacks) {
                if (face != null && face.font() != null) {
                    list.add(face);
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new Face(new Font(Font.SANS_SERIF, Font.PLAIN, 1), null));
        }

        this.sizePx = sizePx;
        int count = list.size();

        Font[] fonts = new Font[count];
        FontShaper[] slotShapers = new FontShaper[count];
        int band = 1;
        int bandCap = (int) Math.ceil(sizePx * 2 * MAX_BAND_RATIO);

        for (int i = 0; i < count; i++) {
            Face face = list.get(i);
            Font derived = face.font().deriveFont(sizePx * 2);
            fonts[i] = derived;
            slotShapers[i] = face.shaper() != null && face.shaper().isUsable() ? face.shaper() : null;
            band = Math.max(band, Math.min(GlyphGenerator.lineHeightPx(derived), bandCap));
        }

        this.slotFonts = fonts;
        this.shapers = slotShapers;
        this.glyphsBySlot = new Glyph[count][];
        this.lineHeightPx = band;
        this.font = fonts[0];
        this.fontHeight = band;
        this.fallBackFonts = count > 1 ? Arrays.copyOfRange(fonts, 1, count) : null;
        this.kerningCache.clear();

        boolean available = false;
        for (int i = 0; i < count; i++) {
            if (fonts[i] != null && slotShapers[i] != null && slotShapers[i].isUsable()) {
                available = true;
                break;
            }
        }
        this.shapingAvailable = available;
    }

    private void reset() {
        allGlyphs = new Glyph['￿' + 1];
        glyphsBySlot = new Glyph[slotFonts.length][];
        stringWidthMapD.clear();
        wrappedLineCache.clear();
        metricsCache.clear();
        kerningCache.clear();
        layoutGeneration++;
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

    public double fontHeight = -1;
    final Object fontHeightLock = new Object();

    private static char fold(char c) {
        return switch (c) {
            case '（' -> '(';
            case '）' -> ')';
            case '・' -> '·';
            case '\t' -> ' ';
            default -> c;
        };
    }

    private static String fold(String text) {
        if (text.indexOf('（') < 0 && text.indexOf('）') < 0
                && text.indexOf('・') < 0 && text.indexOf('\t') < 0) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = fold(chars[i]);
        }
        return new String(chars);
    }

    private int slotFor(int codePoint) {
        for (int i = 0; i < slotFonts.length; i++) {
            Font slotFont = slotFonts[i];
            if (slotFont != null && slotFont.canDisplay(codePoint)) {
                if (i == 0 || shapers[i] != null) {
                    return i;
                }
            }
        }
        return 0;
    }

    public CharMetrics metrics(String text) {
        if (!advancedShaping || !shapingAvailable || text == null || text.isEmpty() || closed) {
            return null;
        }
        synchronized (glyphLock) {
            CharMetrics cached = metricsCache.get(text);
            if (cached != null && cached.generation() == layoutGeneration) {
                return cached;
            }
        }

        CharMetrics built = buildMetrics(text);
        if (built != null) {
            synchronized (glyphLock) {
                metricsCache.put(text, built);
            }
        }
        return built;
    }

    private CharMetrics buildMetrics(String text) {
        try {
            return buildMetricsUnsafe(text);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            shapingAvailable = false;
            return null;
        }
    }

    private CharMetrics buildMetricsUnsafe(String text) {
        String folded = fold(text);
        int length = folded.length();
        if (length == 0) {
            return null;
        }
        if (length > 4096) {
            return null;
        }

        for (int i = 0; i < slotFonts.length; i++) {
            if (shapers[i] != null && !shapers[i].isUsable()) {
                return null;
            }
        }

        List<ShapedGlyph> glyphs = new ArrayList<>(length);
        int index = 0;
        while (index < length) {
            int codePoint = folded.codePointAt(index);
            int slot = slotFor(codePoint);
            FontShaper shaper = slot < shapers.length ? shapers[slot] : null;
            if (shaper == null || !shaper.isUsable()) {
                return null;
            }

            int end = index + Character.charCount(codePoint);
            while (end < length) {
                int next = folded.codePointAt(end);
                if (slotFor(next) != slot) {
                    break;
                }
                end += Character.charCount(next);
            }

            List<ShapedGlyph> run = shaper.shape(folded, index, end, sizePx * 2f, slot,
                    FontShaper.discretionaryLigatures);
            if (run.isEmpty()) {
                return null;
            }
            glyphs.addAll(run);
            index = end;
        }

        if (glyphs.isEmpty()) {
            return null;
        }

        int glyphCount = glyphs.size();
        ShapedGlyph[] array = glyphs.toArray(new ShapedGlyph[0]);
        int[] glyphCharIndex = new int[glyphCount];
        float[] glyphX = new float[glyphCount];
        float[] charAdvance = new float[length];
        float[] charX = new float[length + 1];

        float pen = 0f;
        for (int i = 0; i < glyphCount; i++) {
            ShapedGlyph glyph = array[i];
            int cluster = Math.max(0, Math.min(length - 1, glyph.cluster()));
            glyphCharIndex[i] = cluster;
            glyphX[i] = pen;
            pen += glyph.xAdvance();
            charAdvance[cluster] += glyph.xAdvance();
        }

        float acc = 0f;
        for (int i = 0; i < length; i++) {
            charX[i] = acc;
            acc += charAdvance[i];
        }
        charX[length] = acc;

        return new CharMetrics(text, array, glyphCharIndex, glyphX, charX, charAdvance, acc, layoutGeneration);
    }

    private Glyph locateGlyph(char ch) {
        synchronized (glyphLock) {
            Glyph glyph = allGlyphs[ch];
            if (glyph != null && glyph.uploaded) {
                return glyph;
            }
            if (glyph == null && !closed && this.font != null) {
                GlyphGenerator.generateChar(this, ch, font, fallBackFonts, lineHeightPx, atlas, layoutGeneration, height -> {
                    synchronized (fontHeightLock) {
                        this.fontHeight = Math.max(this.fontHeight, height);
                    }
                });
            }
            return null;
        }
    }

    public Glyph glyphForChar(char ch) {
        char folded = fold(ch);
        Glyph glyph = locateGlyph(folded);
        return glyph == null ? allGlyphs[folded] : glyph;
    }

    public Glyph locateShapedGlyph(ShapedGlyph shaped) {
        int slot = shaped.fontSlot();
        if (slot < 0 || slot >= slotFonts.length || slotFonts[slot] == null) {
            return null;
        }
        synchronized (glyphLock) {
            Glyph[] table = glyphsBySlot[slot];
            if (table == null) {
                int count = Math.max(1, slotFonts[slot].getNumGlyphs());
                FontShaper shaper = slot < shapers.length ? shapers[slot] : null;
                if (shaper != null && shaper.isUsable()) {
                    count = Math.max(count, shaper.glyphCount());
                }
                table = new Glyph[count];
                glyphsBySlot[slot] = table;
            }
            int id = shaped.glyphId();
            if (id < 0 || id >= table.length) {
                return null;
            }
            Glyph glyph = table[id];
            if (glyph != null && glyph.uploaded) {
                return glyph;
            }
            if (glyph == null && !closed) {
                GlyphGenerator.generateById(this, slot, slotFonts[slot], id, lineHeightPx, atlas,
                        layoutGeneration, height -> {
                        });
            }
            return null;
        }
    }

    void storeShapedGlyph(int slot, int glyphId, Glyph glyph) {
        synchronized (glyphLock) {
            Glyph[] table = slot < glyphsBySlot.length ? glyphsBySlot[slot] : null;
            if (table != null && glyphId >= 0 && glyphId < table.length) {
                table[glyphId] = glyph;
            }
        }
    }

    void discardShapedGlyph(int slot, int glyphId, Glyph glyph) {
        synchronized (glyphLock) {
            Glyph[] table = slot < glyphsBySlot.length ? glyphsBySlot[slot] : null;
            if (table != null && glyphId >= 0 && glyphId < table.length
                    && table[glyphId] == glyph && !glyph.uploaded) {
                table[glyphId] = null;
            }
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
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int[] range : lineRanges(text)) {
            for (TextRun run : colorRuns(text.substring(range[0], range[1]), range[0], 0xFFFFFFFF, 1f)) {
                CharMetrics metrics = metrics(run.text());
                if (metrics == null) {
                    prewarmLegacy(run.text());
                    continue;
                }
                for (int i = 0; i < metrics.glyphCount(); i++) {
                    locateShapedGlyph(metrics.glyph(i));
                }
            }
        }
    }

    private void prewarmLegacy(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
                continue;
            }
            locateGlyph(fold(c));
        }
    }

    public void drawGlyphAt(Glyph glyph, double x, double y, int color) {
        if (glyph == null || !glyph.uploaded || glyph.atlasIdentifier == null) {
            return;
        }
        GuiGraphics graphics = RenderContext.graphics();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) x, (float) (y - 2.0f), 0f);
        pose.scale(0.5f, 0.5f, 1f);
        drawGlyph(graphics, glyph, 0, 0, color, color);
        pose.popPose();
    }

    public float drawString(String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float a = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;
        drawString(s, x, y, r, g, b, a);
        return (float) getStringWidthD(s);
    }

    public float drawStringWithVerticalOffsets(String text, double x, double y, int color,
                                               double[] verticalOffsets, int offsetStart) {
        GuiGraphics graphics = RenderContext.graphics();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) x, (float) (y - 2.0), 0f);
        pose.scale(0.5f, 0.5f, 1f);

        drawShapedText(text, color, verticalOffsets, offsetStart, true);

        pose.popPose();
        return (float) getStringWidthD(text);
    }

    public float drawCharGradient(char c, double x, double y, int color, float leftAlphaMul, float rightAlphaMul) {
        float r = ((color >> 16) & 0xff) * RGBA.DIVIDE_BY_255;
        float g = ((color >> 8) & 0xff) * RGBA.DIVIDE_BY_255;
        float b = ((color) & 0xff) * RGBA.DIVIDE_BY_255;
        float baseA = ((color >> 24) & 0xff) * RGBA.DIVIDE_BY_255;

        GuiGraphics graphics = RenderContext.graphics();
        PoseStack pose = graphics.pose();

        y -= 2.0f;

        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale(0.5f, 0.5f, 1f);

        int leftColor = packColor(r, g, b, baseA * leftAlphaMul);
        int rightColor = packColor(r, g, b, baseA * rightAlphaMul);

        CharMetrics metrics = metrics(String.valueOf(fold(c)));
        Glyph glyph = null;
        float advance = 0f;
        if (metrics != null && metrics.glyphCount() > 0) {
            ShapedGlyph shaped = metrics.glyph(0);
            glyph = locateShapedGlyph(shaped);
            advance = shaped.xAdvance();
        } else {
            glyph = locateGlyph(fold(c));
            if (glyph == null) {
                glyph = allGlyphs[fold(c)];
            }
            advance = glyph == null ? 0f : glyph.advance();
        }

        if (glyph != null && glyph.uploaded) {
            if (glyph.atlasIdentifier != null) {
                drawGlyph(graphics, glyph, 0, 0, leftColor, rightColor);
            }
            pose.popPose();
            return advance * 0.5f;
        }

        pose.popPose();
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

    private static int getColorCode(char c) {
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
        GuiGraphics graphics = RenderContext.graphics();
        PoseStack pose = graphics.pose();

        y -= 2.0f;

        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale(0.5f, 0.5f, 1f);

        boolean allLoaded = drawShapedText(s, packColor(r, g, b, a), null, 0, false);

        pose.popPose();
        return allLoaded;
    }

    private boolean drawShapedText(String s, int baseColor, double[] verticalOffsets,
                                   int offsetStart, boolean useOffsets) {
        boolean allLoaded = true;
        List<GlyphBatch> batches = new ArrayList<>();
        double penY = 0;
        double lineAdvance = this.getHeight() * 2 + 4;

        for (int[] range : lineRanges(s)) {
            double penX = 0;
            String line = s.substring(range[0], range[1]);
            for (TextRun run : colorRuns(line, range[0], baseColor, ((baseColor >> 24) & 0xff) / 255f)) {
                CharMetrics metrics = metrics(run.text());
                if (metrics == null) {
                    if (!drawRunLegacy(batches, run, penX, penY, verticalOffsets, offsetStart, useOffsets)) {
                        allLoaded = false;
                    }
                } else if (!drawRun(batches, metrics, run, penX, penY, verticalOffsets, offsetStart, useOffsets)) {
                    allLoaded = false;
                }
                penX += run.width(this);
            }
            penY += lineAdvance;
        }

        drawBatches(batches);
        return allLoaded;
    }

    private boolean drawRun(List<GlyphBatch> batches, CharMetrics metrics, TextRun run,
                            double penX, double penY, double[] verticalOffsets, int offsetStart, boolean useOffsets) {
        boolean allLoaded = true;

        for (int i = 0; i < metrics.glyphCount(); i++) {
            ShapedGlyph shaped = metrics.glyph(i);
            Glyph glyph = locateShapedGlyph(shaped);
            if (glyph == null || !glyph.uploaded) {
                allLoaded = false;
                continue;
            }

            double glyphX = penX + metrics.glyphX(i) + shaped.xOffset();
            double glyphY = penY + shaped.yOffset();
            if (useOffsets && verticalOffsets != null) {
                int index = offsetStart + run.start() + metrics.charIndexOfGlyph(i);
                if (index >= 0 && index < verticalOffsets.length) {
                    glyphY -= verticalOffsets[index] * 2.0;
                }
            }

            if (glyph.atlasIdentifier != null) {
                addGlyph(batches, glyph, (float) glyphX, (float) glyphY, run.color(), run.color());
            }
        }

        return allLoaded;
    }

    private boolean drawRunLegacy(List<GlyphBatch> batches, TextRun run, double penX, double penY,
                                  double[] verticalOffsets, int offsetStart, boolean useOffsets) {
        String text = run.text();
        boolean allLoaded = true;
        double x = penX;

        for (int i = 0; i < text.length(); i++) {
            char c = fold(text.charAt(i));
            char next = i + 1 < text.length() ? fold(text.charAt(i + 1)) : '\0';

            Glyph glyph = locateGlyph(c);
            if (glyph == null) {
                glyph = allGlyphs[c];
            }
            if (glyph == null) {
                allLoaded = false;
                continue;
            }
            if (!glyph.uploaded) {
                allLoaded = false;
            }

            double glyphY = penY;
            if (useOffsets && verticalOffsets != null) {
                int index = offsetStart + run.start() + i;
                if (index >= 0 && index < verticalOffsets.length) {
                    glyphY -= verticalOffsets[index] * 2.0;
                }
            }

            if (glyph.uploaded && glyph.atlasIdentifier != null) {
                addGlyph(batches, glyph, (float) x, (float) glyphY, run.color(), run.color());
            }

            x += glyph.advance();
            if (next != '\0') {
                x += pairKerning(c, next) * 2;
            }
        }

        return allLoaded;
    }

    private float pairKerning(char left, char right) {
        FontShaper shaper = shapers.length > 0 ? shapers[0] : null;
        if (shaper == null || !shaper.isUsable()) {
            return 0f;
        }

        long key = ((long) left << 16) | (right & 0xFFFFL);
        synchronized (kerningCache) {
            Float cached = kerningCache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        float value;
        try {
            int size = Math.max(1, Math.round(sizePx * 2f));
            float leftAdvance = advanceOf(shaper, String.valueOf(left), size);
            float rightAdvance = advanceOf(shaper, String.valueOf(right), size);
            float pairAdvance = advanceOf(shaper, new String(new char[]{left, right}), size);
            value = pairAdvance - leftAdvance - rightAdvance;
        } catch (Throwable throwable) {
            value = 0f;
        }

        synchronized (kerningCache) {
            kerningCache.put(key, value);
        }
        return value;
    }

    private static float advanceOf(FontShaper shaper, String text, int size) {
        List<ShapedGlyph> glyphs = shaper.shape(text, 0, text.length(), size, 0, false);
        float advance = 0f;
        for (ShapedGlyph glyph : glyphs) {
            advance += glyph.xAdvance();
        }
        return advance;
    }

    private static void drawGlyph(GuiGraphics graphics, Glyph glyph, float x, float y,
                                  int leftColor, int rightColor) {
        Render.glyph(graphics, glyph.atlasIdentifier,
                x + glyph.originX, y + glyph.originY, glyph.bitmapWidth, glyph.bitmapHeight,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1, leftColor, rightColor);
    }

    private static void addGlyph(List<GlyphBatch> batches, Glyph glyph, float x, float y,
                                 int leftColor, int rightColor) {
        ResourceLocation atlas = glyph.atlasIdentifier;
        if (atlas == null) {
            return;
        }
        GlyphBatch batch;
        if (batches.isEmpty() || !atlas.equals(batches.get(batches.size() - 1).atlas)) {
            batch = new GlyphBatch(atlas);
            batches.add(batch);
        } else {
            batch = batches.get(batches.size() - 1);
        }
        batch.quads.add(new Render.GlyphQuad(x + glyph.originX, y + glyph.originY,
                glyph.bitmapWidth, glyph.bitmapHeight,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1, leftColor, rightColor));
    }

    private static void drawBatches(List<GlyphBatch> batches) {
        for (GlyphBatch batch : batches) {
            Render.glyphs(RenderContext.graphics(), batch.atlas, batch.quads);
        }
    }

    private static final class GlyphBatch {
        private final ResourceLocation atlas;
        private final List<Render.GlyphQuad> quads = new ArrayList<>();

        private GlyphBatch(ResourceLocation atlas) {
            this.atlas = atlas;
        }
    }

    private static int packColor(float r, float g, float b, float a) {
        return RGBA.color((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
    }

    private record TextRun(int start, String text, int color) {

        float width(CFontRenderer renderer) {
            CharMetrics metrics = renderer.metrics(text);
            if (metrics != null) {
                return metrics.width();
            }
            return (float) renderer.measureLegacyPx(text);
        }
    }

    private double measureLegacyPx(String text) {
        double width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = fold(text.charAt(i));
            Glyph glyph = locateGlyph(c);
            if (glyph == null) {
                glyph = allGlyphs[c];
            }
            if (glyph == null) {
                continue;
            }
            width += glyph.advance();
            if (i + 1 < text.length()) {
                width += pairKerning(c, fold(text.charAt(i + 1))) * 2;
            }
        }
        return width;
    }

    private static List<int[]> lineRanges(String s) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                ranges.add(new int[]{start, i});
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        ranges.add(new int[]{start, s.length()});
        return ranges;
    }

    private static List<TextRun> colorRuns(String line, int lineStart, int baseColor, float alpha) {
        List<TextRun> runs = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int runStart = lineStart;
        int color = baseColor;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '§') {
                if (!builder.isEmpty()) {
                    runs.add(new TextRun(runStart, builder.toString(), color));
                    builder.setLength(0);
                }
                if (i + 1 < line.length()) {
                    char code = line.charAt(i + 1);
                    if (code == 'r') {
                        color = baseColor;
                    } else {
                        int value = getColorCode(code);
                        if (value != Integer.MIN_VALUE) {
                            color = RGBA.color(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF,
                                    (int) (alpha * 255));
                        }
                    }
                    i++;
                }
                runStart = lineStart + i + 1;
                continue;
            }
            builder.append(c);
        }

        if (!builder.isEmpty()) {
            runs.add(new TextRun(runStart, builder.toString(), color));
        }
        return runs;
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
    private final Map<WrapKey, List<WrappedLine>> wrappedLineCache = new LinkedHashMap<>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WrapKey, List<WrappedLine>> eldest) {
            return size() > 512;
        }
    };

    public boolean areGlyphsLoaded(String text) {
        for (int[] range : lineRanges(text)) {
            for (TextRun run : colorRuns(text.substring(range[0], range[1]), range[0], 0xFFFFFFFF, 1f)) {
                CharMetrics metrics = metrics(run.text());
                if (metrics != null) {
                    for (int i = 0; i < metrics.glyphCount(); i++) {
                        Glyph glyph = locateShapedGlyph(metrics.glyph(i));
                        if (glyph == null || !glyph.uploaded) {
                            return false;
                        }
                    }
                    continue;
                }
                for (int i = 0; i < run.text().length(); i++) {
                    char c = run.text().charAt(i);
                    if (c == ' ' || c == '\t') {
                        continue;
                    }
                    Glyph glyph = allGlyphs[fold(c)];
                    if (glyph == null || !glyph.uploaded) {
                        locateGlyph(fold(c));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public double getStringWidthD(String text) {
        Double f = this.stringWidthMapD.get(text);
        if (f != null)
            return f;

        double width = measureShaped(text);
        if (width < 0) {
            width = 0;
            for (int[] range : lineRanges(text)) {
                String line = stripControlCodes(text.substring(range[0], range[1]));
                width = Math.max(width, measureLegacy(line));
            }
        }

        this.stringWidthMapD.put(text, width);
        return width;
    }

    private double measureShaped(String text) {
        if (!advancedShaping || !shapingAvailable || closed) {
            return -1;
        }
        double max = 0;
        boolean any = false;
        for (int[] range : lineRanges(text)) {
            double lineWidth = 0;
            for (TextRun run : colorRuns(text.substring(range[0], range[1]), range[0], 0xFFFFFFFF, 1f)) {
                CharMetrics metrics = metrics(run.text());
                if (metrics == null) {
                    return -1;
                }
                lineWidth += metrics.width() * 0.5;
                any = true;
            }
            max = Math.max(max, lineWidth);
        }
        return any ? max : -1;
    }

    private double measureLegacy(String text) {
        char[] c = text.toCharArray();
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

            currentLine += getCharWidth(c1, c2);
        }
        return Math.max(currentLine, maxPreviousLines);
    }

    public int getHeight() {
        return (int) this.getFontHeight();
    }

    public net.minecraft.resources.ResourceLocation getAtlasId() {
        return atlas.identifier();
    }

    public Glyph[] getAllGlyphs() {
        return allGlyphs;
    }

    public int getGlyphSlotCount() {
        return slotFonts.length;
    }

    public Font slotFont(int slot) {
        return slot >= 0 && slot < slotFonts.length ? slotFonts[slot] : null;
    }

    public NativeImage getAtlasImage() {
        return atlas.getImage();
    }

    public double getFontHeight() {
        return (this.fontHeight - 8.5) * .5;
    }

    @Override
    public void close() {
        synchronized (glyphLock) {
            if (closed) {
                return;
            }
            closed = true;
            atlas.destroy();
            font = null;
            fallBackFonts = null;
            shapers = new FontShaper[1];
            slotFonts = new Font[1];
            reset();
        }
    }

    float getCharWidth(char ch) {
        return getCharWidth(ch, '\0');
    }

    public float getCharWidth(char ch, char nextChar) {
        String pair = nextChar == '\0'
                ? String.valueOf(ch)
                : new String(new char[]{ch, nextChar});

        CharMetrics metrics = metrics(pair);
        if (metrics != null && metrics.glyphCount() > 0) {
            return metrics.advanceOfChar(0);
        }

        char folded = fold(ch);
        Glyph glyph = allGlyphs[folded];
        if (glyph == null) {
            locateGlyph(folded);
            glyph = allGlyphs[folded];
            if (glyph == null) {
                return .0f;
            }
        }

        float width = glyph.advance() * .5f;
        if (nextChar != '\0') {
            width += pairKerning(folded, fold(nextChar));
        }
        return width;
    }

    public double getStringHeight(String text) {
        return text.split("\n").length * (getFontHeight() + 4) - 4;
    }

    public String[] fitWidth(String text, double width) {
        List<WrappedLine> wrappedLines = fitWidthLines(text, width);
        String[] lines = new String[wrappedLines.size()];
        for (int i = 0; i < wrappedLines.size(); i++) {
            lines[i] = wrappedLines.get(i).text();
        }
        return lines;
    }

    public List<WrappedLine> fitWidthLines(String text, double width) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        double effectiveWidth = adaptiveWidth(width);
        WrapKey key = new WrapKey(text, Double.doubleToLongBits(effectiveWidth));
        List<WrappedLine> cached = wrappedLineCache.get(key);
        if (cached != null) {
            return cached;
        }

        CharMetrics metrics = metrics(text);
        List<WrappedLine> lines = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            int previousI = i;
            LineBreakResult result = findLineBreak(text, i, effectiveWidth, metrics);

            lines.add(new WrappedLine(text.substring(i, result.endIndex), i, result.endIndex));

            i = skipLineStartWhitespace(text, result.nextStartIndex);

            if (i == previousI) {
                i++;
            }
        }

        List<WrappedLine> result = List.copyOf(lines);
        if (isLayoutStable(text)) {
            wrappedLineCache.put(key, result);
        }
        return result;
    }

    public record WrappedLine(String text, int startIndex, int endIndex) {
    }

    private record WrapKey(String text, long widthBits) {
    }

    private double adaptiveWidth(double width) {
        if (Double.isNaN(width) || width <= 0) {
            return 0;
        }
        if (Double.isInfinite(width)) {
            return Double.MAX_VALUE;
        }
        return Math.floor(width * 16) / 16;
    }

    private int skipLineStartWhitespace(String text, int index) {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c != ' ' && c != '\t') {
                break;
            }
            index++;
        }
        return index;
    }

    public boolean isLayoutStable(String text) {
        CharMetrics metrics = metrics(text);
        if (metrics != null) {
            for (int i = 0; i < metrics.glyphCount(); i++) {
                if (locateShapedGlyph(metrics.glyph(i)) == null) {
                    return false;
                }
            }
            return true;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\247' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
                continue;
            }
            if (c == '（') c = '(';
            if (c == '）') c = ')';
            if (c == '・') c = '·';
            Glyph glyph = allGlyphs[c];
            if (glyph == null) {
                return false;
            }
        }
        return true;
    }

    public long getLayoutGeneration() {
        return layoutGeneration;
    }

    static final char[] breakableChars = new char['￿' + 1];
    static String breakable = " \t.-‐‑‒–—…。,，!！?？:：;；、";
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

    private double charWidth(int index, String text, CharMetrics metrics) {
        if (metrics != null) {
            return metrics.advanceOfChar(index) * 0.5;
        }
        char c = text.charAt(index);
        char nextChar = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
        return getCharWidth(c, nextChar);
    }

    private LineBreakResult findLineBreak(String text, int startIndex, double maxWidth, CharMetrics metrics) {
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

            if (c == '\n' || c == '\r') {
                int nextStartIndex = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                return new LineBreakResult(i, nextStartIndex);
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
                    lastBreakableTrimThisChar = c == ' ' || c == '\t';
                }
            }

            double charWidth = charWidth(i, text, metrics);

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

    private record LineBreakResult(int endIndex, int nextStartIndex) {
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
