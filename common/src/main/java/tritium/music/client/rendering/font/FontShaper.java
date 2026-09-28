package tritium.music.client.rendering.font;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.harfbuzz.hb_feature_t;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public final class FontShaper {

    public static volatile boolean discretionaryLigatures = false;

    private static final String[] LIGATURE_FEATURES = {"dlig=1"};
    private static final String PROBE_CHARS = "AazZ0mWilM.,;:!?@#&%()[]{}\u00e9\u00fc\u4e2d\u6587";

    private static volatile String language;

    private final String description;

    private long ftLibrary;
    private long ftFaceAddress;
    private FT_Face ftFace;

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

            long library;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer libraryPtr = stack.mallocPointer(1);
                checkFT(FT_Init_FreeType(libraryPtr));
                library = libraryPtr.get();
            }
            ftLibrary = library;

            fontData = memAlloc(bytes.length);
            fontData.put(bytes);
            fontData.flip();

            hbBlob = hb_blob_create(fontData, fontData.remaining(), HB_MEMORY_MODE_READONLY, null);
            if (hbBlob == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz blob");
            }

            faceIndex = selectFace(postScriptName);

            ftFaceAddress = openFace(faceIndex);
            if (ftFaceAddress == NULL) {
                throw new IllegalStateException("Failed to open FreeType face " + faceIndex);
            }
            ftFace = FT_Face.create(ftFaceAddress);

            hbFace = hb_face_create(hbBlob, faceIndex);
            if (hbFace == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz face");
            }

            hbFont = hb_font_create(hbFace);
            if (hbFont == NULL) {
                throw new IllegalStateException("Failed to create HarfBuzz font");
            }

            hbBuffer = hb_buffer_create();
            if (hbBuffer == NULL || !hb_buffer_allocation_successful(hbBuffer)) {
                throw new IllegalStateException("Failed to allocate HarfBuzz buffer");
            }

            glyphCount = Math.max(1, hb_face_get_glyph_count(hbFace));
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
                if (!hb_font_get_nominal_glyph(hbFont, codePoint, glyph)) {
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

            hb_buffer_clear_contents(hbBuffer);
            hb_buffer_set_direction(hbBuffer, HB_DIRECTION_INVALID);
            hb_buffer_set_script(hbBuffer, HB_SCRIPT_INVALID);
            hb_buffer_add_utf16(hbBuffer, text, start, end - start);
            hb_buffer_guess_segment_properties(hbBuffer);
            hb_buffer_set_language(hbBuffer, hb_language_from_string(language()));

            if (discretionaryLigatures) {
                hb_feature_t.Buffer features = hb_feature_t.malloc(LIGATURE_FEATURES.length);
                try {
                    for (int i = 0; i < LIGATURE_FEATURES.length; i++) {
                        hb_feature_from_string(LIGATURE_FEATURES[i], features.get(i));
                    }
                    hb_shape(hbFont, hbBuffer, features);
                } finally {
                    features.free();
                }
            } else {
                hb_shape(hbFont, hbBuffer, null);
            }

            hb_glyph_info_t.Buffer infos = hb_buffer_get_glyph_infos(hbBuffer);
            hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(hbBuffer);
            if (infos == null || positions == null) {
                return result;
            }

            int count = Math.min(infos.remaining(), positions.remaining());
            for (int i = 0; i < count; i++) {
                hb_glyph_info_t info = infos.get(i);
                hb_glyph_position_t position = positions.get(i);
                result.add(new ShapedGlyph(
                        fontSlot,
                        info.codepoint(),
                        info.cluster(),
                        position.x_advance() / 64.0f,
                        position.y_advance() / 64.0f,
                        position.x_offset() / 64.0f,
                        position.y_offset() / 64.0f));
            }
        }

        return result;
    }

    private void setSize(float pixelSize) {
        int scale = Math.max(1, Math.round(pixelSize * 64.0f));
        hb_font_set_scale(hbFont, scale, scale);
    }

    private int selectFace(String postScriptName) {
        int faces = Math.max(1, hb_face_count(hbBlob));
        if (faces <= 1 || postScriptName == null || postScriptName.isBlank()) {
            return 0;
        }

        String wanted = postScriptName.trim().toLowerCase(Locale.ROOT);
        int regular = -1;
        for (int index = 0; index < faces; index++) {
            long address = openFace(index);
            if (address == NULL) {
                continue;
            }
            FT_Face face = FT_Face.create(address);
            try {
                String name = FT_Get_Postscript_Name(face);
                if (name == null) {
                    continue;
                }
                String normalized = name.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals(wanted)) {
                    return index;
                }
                if (regular < 0 && normalized.contains("regular")) {
                    regular = index;
                }
            } catch (Throwable ignored) {
            } finally {
                FT_Done_Face(face);
            }
        }
        return regular < 0 ? 0 : regular;
    }

    private long openFace(int index) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer facePtr = stack.mallocPointer(1);
            int error = FT_New_Memory_Face(ftLibrary, fontData, index, facePtr);
            if (error != FT_Err_Ok) {
                return NULL;
            }
            return facePtr.get(0);
        }
    }

    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        usable = false;

        if (hbBuffer != NULL) {
            hb_buffer_destroy(hbBuffer);
            hbBuffer = NULL;
        }
        if (hbFont != NULL) {
            hb_font_destroy(hbFont);
            hbFont = NULL;
        }
        if (hbFace != NULL) {
            hb_face_destroy(hbFace);
            hbFace = NULL;
        }
        if (hbBlob != NULL) {
            hb_blob_destroy(hbBlob);
            hbBlob = NULL;
        }
        if (ftFaceAddress != NULL) {
            FT_Done_Face(ftFace);
            ftFaceAddress = NULL;
            ftFace = null;
        }
        if (ftLibrary != NULL) {
            FT_Done_FreeType(ftLibrary);
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
        if (err != FT_Err_Ok) {
            throw new IllegalStateException("FreeType error: " + err);
        }
    }
}
