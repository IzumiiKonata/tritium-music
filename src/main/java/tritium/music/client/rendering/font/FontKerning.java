package tritium.music.client.rendering.font;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.freetype.FreeType.FT_ENCODING_UNICODE;
import static org.lwjgl.util.freetype.FreeType.FT_Done_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Done_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_Err_Ok;
import static org.lwjgl.util.freetype.FreeType.FT_Init_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Select_Charmap;
import static org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes;
import static org.lwjgl.util.harfbuzz.HarfBuzz.HB_MEMORY_MODE_READONLY;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_blob_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_blob_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_add_utf8;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_allocation_successful;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_get_glyph_positions;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_guess_segment_properties;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_reset;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_set_language;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_face_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_face_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_set_ppem;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_set_scale;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_language_from_string;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_shape;
import static org.lwjgl.util.harfbuzz.OpenType.hb_ot_font_set_funcs;

public final class FontKerning {

    private final long hbFont;
    private final long hbBuffer;
    private final long hbBlob;

    private final long ftLibrary;
    private final long ftFaceAddress;
    private final FT_Face ftFace;
    private final ByteBuffer fontData;

    private final Map<Long, Float> kerningCache = new HashMap<>();

    public FontKerning(String resPath) {
        try {
            InputStream resourceAsStream = FontKerning.class.getResourceAsStream(resPath);

            if (resourceAsStream == null) {
                throw new RuntimeException("Failed to find resource: " + resPath);
            }

            byte[] fileBytes = resourceAsStream.readAllBytes();
            resourceAsStream.close();

            long tempLibrary;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer libraryPtr = stack.mallocPointer(1);
                checkFT(FT_Init_FreeType(libraryPtr));
                tempLibrary = libraryPtr.get();
            }
            ftLibrary = tempLibrary;

            fontData = memAlloc(fileBytes.length);
            fontData.put(fileBytes);
            fontData.flip();

            long tempFace;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer facePtr = stack.mallocPointer(1);
                checkFT(FT_New_Memory_Face(ftLibrary, fontData, 0, facePtr));
                tempFace = facePtr.get(0);
            }

            if (tempFace == NULL) {
                throw new RuntimeException("FT_New_Memory_Face returned NULL face");
            }

            ftFaceAddress = tempFace;
            ftFace = FT_Face.create(ftFaceAddress);

            checkFT(FT_Select_Charmap(ftFace, FT_ENCODING_UNICODE));
            checkFT(FT_Set_Pixel_Sizes(ftFace, 0, 16));

            hbBlob = hb_blob_create(fontData, fontData.remaining(), HB_MEMORY_MODE_READONLY, null);
            if (hbBlob == NULL) {
                throw new RuntimeException("Failed to create HarfBuzz blob");
            }

            long hbFace = hb_face_create(hbBlob, 0);
            if (hbFace == NULL) {
                throw new RuntimeException("Failed to create HarfBuzz face");
            }

            hbFont = hb_font_create(hbFace);
            hb_face_destroy(hbFace);

            if (hbFont == NULL) {
                throw new RuntimeException("Failed to create HarfBuzz font");
            }

            hb_font_set_scale(hbFont, 16 * 64, 16 * 64);
            hb_font_set_ppem(hbFont, 16, 16);

            hb_ot_font_set_funcs(hbFont);

            hbBuffer = hb_buffer_create();
            if (hbBuffer == NULL || !hb_buffer_allocation_successful(hbBuffer)) {
                throw new RuntimeException("Failed to allocate HarfBuzz buffer");
            }

        } catch (Exception e) {
            dispose();
            throw new RuntimeException("Failed to init FontKerning", e);
        }
    }

    public synchronized float getKerning(char left, char right, float fontSizePx) {
        long key = ((long) left << 48)
                | ((long) right << 32)
                | (Float.floatToRawIntBits(fontSizePx) & 0xFFFFFFFFL);

        Float cached = kerningCache.get(key);
        if (cached != null) {
            return cached;
        }

        setFontSize(fontSizePx);

        float advL = shapeAdvancePx(String.valueOf(left));
        float advR = shapeAdvancePx(String.valueOf(right));
        float advLR = shapeAdvancePx("" + left + right);

        float result = advLR - advL - advR;

        kerningCache.put(key, result);

        return result;
    }

    private float shapeAdvancePx(String text) {
        hb_buffer_reset(hbBuffer);
        hb_buffer_add_utf8(hbBuffer, text, 0, -1);
        hb_buffer_guess_segment_properties(hbBuffer);
        hb_buffer_set_language(hbBuffer, hb_language_from_string("en"));
        hb_shape(hbFont, hbBuffer, null);

        hb_glyph_position_t.Buffer pos = hb_buffer_get_glyph_positions(hbBuffer);

        if (pos == null) {
            return .0f;
        }

        float advance = 0.0f;
        for (int i = 0; i < pos.remaining(); i++) {
            advance += pos.get(i).x_advance();
        }

        return advance / 64.0f;
    }

    private void setFontSize(float px) {
        checkFT(FT_Set_Pixel_Sizes(ftFace, 0, (int) px));

        int scale = (int) (px * 64);
        hb_font_set_scale(hbFont, scale, scale);
        hb_font_set_ppem(hbFont, (int) px, (int) px);
    }

    public void dispose() {
        if (hbBuffer != NULL) {
            hb_buffer_destroy(hbBuffer);
        }
        if (hbFont != NULL) {
            hb_font_destroy(hbFont);
        }
        if (hbBlob != NULL) {
            hb_blob_destroy(hbBlob);
        }
        if (ftFaceAddress != NULL) {
            FT_Done_Face(ftFace);
        }
        if (ftLibrary != NULL) {
            FT_Done_FreeType(ftLibrary);
        }
        if (fontData != null) {
            memFree(fontData);
        }
    }

    private static void checkFT(int err) {
        if (err != FT_Err_Ok) {
            throw new RuntimeException("FreeType error: " + err);
        }
    }
}
