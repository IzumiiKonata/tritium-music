package tritium.music.client.rendering.font;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import tritium.music.client.rendering.font.binding.FreeTypeNative;
import tritium.music.client.rendering.font.binding.HarfBuzzNative;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.system.MemoryUtil.*;

public final class FontShaper {

    public static volatile boolean discretionaryLigatures = false;

    private static final String[] LIGATURE_FEATURES = {"dlig=1"};
    private static final String PROBE_CHARS = "AazZ0mWilM.,;:!?@#&%()[]{}\u00e9\u00fc\u4e2d\u6587";

    private static final int GLYPH_INFO_SIZE = 20;
    private static final int GLYPH_INFO_CODEPOINT = 0;
    private static final int GLYPH_INFO_CLUSTER = 8;
    private static final int GLYPH_POSITION_SIZE = 20;
    private static final int GLYPH_POSITION_X_ADVANCE = 0;
    private static final int GLYPH_POSITION_Y_ADVANCE = 4;
    private static final int GLYPH_POSITION_X_OFFSET = 8;
    private static final int GLYPH_POSITION_Y_OFFSET = 12;
    private static final int FEATURE_SIZE = 16;

    private static volatile String language;

    private final String description;

    private long ftLibrary;
    private long ftFace;

    private long hbBlob;
    private long hbFace;
    private long hbFont;
    private long hbBuffer;

    private ByteBuffer fontData;
    private int faceIndex;
    private int glyphCount;
    private boolean usable;
    private volatile boolean disposed;

    public FontShaper(byte[] bytes, String postScriptName, String description) {
        this.description = description;
        try {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Empty font data: " + description);
            }
            if (!HarfBuzzNative.available() || !FreeTypeNative.available()) {
                throw new IllegalStateException("Font shaping natives unavailable: FreeType[" + FreeTypeNative.status()
                        + "] HarfBuzz[" + HarfBuzzNative.status() + "]");
            }

            long library;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer libraryPointer = stack.mallocPointer(1);
                checkFT(FreeTypeNative.initFreeType(memAddress(libraryPointer)));
                library = libraryPointer.get(0);
            }
            ftLibrary = library;

            fontData = memAlloc(bytes.length);
            fontData.put(bytes);
            fontData.flip();

            hbBlob = HarfBuzzNative.hbBlobCreate(memAddress(fontData), fontData.remaining(),
                    HarfBuzzNative.MEMORY_MODE_READONLY, NULL, NULL);
            if (hbBlob == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz blob");
            }

            faceIndex = selectFace(postScriptName);

            ftFace = openFace(faceIndex);
            if (ftFace == NULL) {
                throw new IllegalStateException("Failed to open FreeType face " + faceIndex);
            }

            hbFace = HarfBuzzNative.hbFaceCreate(hbBlob, faceIndex);
            if (hbFace == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz face");
            }

            hbFont = HarfBuzzNative.hbFontCreate(hbFace);
            if (hbFont == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz font");
            }

            hbBuffer = HarfBuzzNative.hbBufferCreate();
            if (hbBuffer == NULL || !HarfBuzzNative.hbBufferAllocationSuccessful(hbBuffer)) {
                throw new IllegalStateException("Failed to allocate HarfBuzz buffer");
            }

            glyphCount = Math.max(1, HarfBuzzNative.hbFaceGetGlyphCount(hbFace));
            usable = true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            dispose();
            usable = false;
        }
    }

    public boolean isUsable() {
        return usable && !disposed;
    }

    public int glyphCount() {
        return glyphCount;
    }

    public int faceIndex() {
        return faceIndex;
    }

    public String description() {
        return description;
    }

    public boolean hasGlyph(int codePoint) {
        return nominalGlyph(codePoint) >= 0;
    }

    public int nominalGlyph(int codePoint) {
        if (!isUsable()) {
            return -1;
        }
        synchronized (this) {
            if (disposed) {
                return -1;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer glyph = stack.mallocInt(1);
                if (!HarfBuzzNative.hbFontGetNominalGlyph(hbFont, codePoint, memAddress(glyph))) {
                    return -1;
                }
                return glyph.get(0);
            } catch (Throwable throwable) {
                return -1;
            }
        }
    }

    public boolean matchesGlyphIds(java.awt.Font awtFont) {
        if (!isUsable() || awtFont == null) {
            return false;
        }

        java.awt.font.FontRenderContext context = new java.awt.font.FontRenderContext(null, true, true);
        int checked = 0;

        for (int i = 0; i < PROBE_CHARS.length(); i++) {
            char c = PROBE_CHARS.charAt(i);
            if (!awtFont.canDisplay(c)) {
                continue;
            }
            int nominal = nominalGlyph(c);
            if (nominal < 0) {
                continue;
            }
            int awtGlyph;
            try {
                awtGlyph = awtFont.createGlyphVector(context, String.valueOf(c)).getGlyphCode(0);
            } catch (Throwable throwable) {
                return false;
            }
            if (awtGlyph != nominal) {
                return false;
            }
            checked++;
        }

        return checked >= 4;
    }

    public List<ShapedGlyph> shape(CharSequence text, int start, int end, float pixelSize,
                                   int fontSlot, boolean discretionaryLigatures) {
        List<ShapedGlyph> result = new ArrayList<>();
        if (!isUsable() || end <= start) {
            return result;
        }

        synchronized (this) {
            if (disposed) {
                return result;
            }

            setSize(pixelSize);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                HarfBuzzNative.hbBufferClearContents(hbBuffer);
                HarfBuzzNative.hbBufferSetDirection(hbBuffer, HarfBuzzNative.DIRECTION_INVALID);
                HarfBuzzNative.hbBufferSetScript(hbBuffer, HarfBuzzNative.SCRIPT_INVALID);

                int textLength = stack.nUTF16(text, false);
                long textAddress = stack.getPointerAddress();
                HarfBuzzNative.hbBufferAddUtf16(hbBuffer, textAddress, textLength, start, end - start);

                HarfBuzzNative.hbBufferGuessSegmentProperties(hbBuffer);

                String currentLanguage = language();
                ByteBuffer languageText = stack.ASCII(currentLanguage);
                long languageHandle = HarfBuzzNative.hbLanguageFromString(memAddress(languageText), currentLanguage.length());
                HarfBuzzNative.hbBufferSetLanguage(hbBuffer, languageHandle);

                if (discretionaryLigatures) {
                    long features = stack.nmalloc(8, FEATURE_SIZE * LIGATURE_FEATURES.length);
                    for (int i = 0; i < LIGATURE_FEATURES.length; i++) {
                        ByteBuffer featureText = stack.ASCII(LIGATURE_FEATURES[i]);
                        HarfBuzzNative.hbFeatureFromString(memAddress(featureText), LIGATURE_FEATURES[i].length(),
                                features + (long) i * FEATURE_SIZE);
                    }
                    HarfBuzzNative.hbShape(hbFont, hbBuffer, features, LIGATURE_FEATURES.length);
                } else {
                    HarfBuzzNative.hbShape(hbFont, hbBuffer, NULL, 0);
                }

                IntBuffer count = stack.mallocInt(1);
                long infos = HarfBuzzNative.hbBufferGetGlyphInfos(hbBuffer, memAddress(count));
                long positions = HarfBuzzNative.hbBufferGetGlyphPositions(hbBuffer, memAddress(count));
                if (infos == NULL || positions == NULL) {
                    return result;
                }

                int glyphs = count.get(0);
                for (int i = 0; i < glyphs; i++) {
                    long info = infos + (long) i * GLYPH_INFO_SIZE;
                    long position = positions + (long) i * GLYPH_POSITION_SIZE;
                    result.add(new ShapedGlyph(
                            fontSlot,
                            memGetInt(info + GLYPH_INFO_CODEPOINT),
                            memGetInt(info + GLYPH_INFO_CLUSTER),
                            memGetInt(position + GLYPH_POSITION_X_ADVANCE) / 64.0f,
                            memGetInt(position + GLYPH_POSITION_Y_ADVANCE) / 64.0f,
                            memGetInt(position + GLYPH_POSITION_X_OFFSET) / 64.0f,
                            memGetInt(position + GLYPH_POSITION_Y_OFFSET) / 64.0f));
                }
            }
        }

        return result;
    }

    private void setSize(float pixelSize) {
        int scale = Math.max(1, Math.round(pixelSize * 64.0f));
        HarfBuzzNative.hbFontSetScale(hbFont, scale, scale);
    }

    private int selectFace(String postScriptName) {
        int faces = Math.max(1, HarfBuzzNative.hbFaceCount(hbBlob));
        if (faces <= 1 || postScriptName == null || postScriptName.isBlank()) {
            return 0;
        }

        String wanted = postScriptName.trim().toLowerCase(Locale.ROOT);
        int regular = -1;
        for (int index = 0; index < faces; index++) {
            long face = openFace(index);
            if (face == NULL) {
                continue;
            }
            try {
                long nameAddress = FreeTypeNative.postScriptNameAddress(face);
                if (nameAddress == NULL) {
                    continue;
                }
                String normalized = memASCIISafe(nameAddress).trim().toLowerCase(Locale.ROOT);
                if (normalized.equals(wanted)) {
                    return index;
                }
                if (regular < 0 && normalized.contains("regular")) {
                    regular = index;
                }
            } catch (Throwable ignored) {
            } finally {
                FreeTypeNative.doneFace(face);
            }
        }
        return regular < 0 ? 0 : regular;
    }

    private long openFace(int index) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer facePointer = stack.mallocPointer(1);
            int error = FreeTypeNative.newMemoryFace(ftLibrary, memAddress(fontData), fontData.remaining(), index,
                    memAddress(facePointer));
            if (error != FreeTypeNative.FT_ERR_OK) {
                return NULL;
            }
            return facePointer.get(0);
        }
    }

    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        usable = false;

        if (hbBuffer != NULL) {
            HarfBuzzNative.hbBufferDestroy(hbBuffer);
            hbBuffer = NULL;
        }
        if (hbFont != NULL) {
            HarfBuzzNative.hbFontDestroy(hbFont);
            hbFont = NULL;
        }
        if (hbFace != NULL) {
            HarfBuzzNative.hbFaceDestroy(hbFace);
            hbFace = NULL;
        }
        if (hbBlob != NULL) {
            HarfBuzzNative.hbBlobDestroy(hbBlob);
            hbBlob = NULL;
        }
        if (ftFace != NULL) {
            FreeTypeNative.doneFace(ftFace);
            ftFace = NULL;
        }
        if (ftLibrary != NULL) {
            FreeTypeNative.doneFreeType(ftLibrary);
            ftLibrary = NULL;
        }
        if (fontData != null) {
            memFree(fontData);
            fontData = null;
        }
    }

    private static String language() {
        String cached = language;
        if (cached == null) {
            cached = Locale.getDefault().getLanguage();
            if (cached == null || cached.isBlank()) {
                cached = "en";
            }
            language = cached;
        }
        return cached;
    }

    private static void checkFT(int err) {
        if (err != FreeTypeNative.FT_ERR_OK) {
            throw new IllegalStateException("FreeType error: " + err);
        }
    }
}
