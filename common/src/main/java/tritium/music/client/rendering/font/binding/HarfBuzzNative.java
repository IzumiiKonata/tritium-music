package tritium.music.client.rendering.font.binding;

import static org.lwjgl.system.JNI.invokeP;
import static org.lwjgl.system.JNI.invokePI;
import static org.lwjgl.system.JNI.invokePP;
import static org.lwjgl.system.JNI.invokePPI;
import static org.lwjgl.system.JNI.invokePPP;
import static org.lwjgl.system.JNI.invokePPPP;
import static org.lwjgl.system.JNI.invokePPPPPV;
import static org.lwjgl.system.JNI.invokePPPV;
import static org.lwjgl.system.JNI.invokePPV;
import static org.lwjgl.system.JNI.invokePV;

public final class HarfBuzzNative {

    public static final int MEMORY_MODE_READONLY = 1;
    public static final int DIRECTION_INVALID = 0;
    public static final int SCRIPT_INVALID = 0;

    private static final NativeLoader LOADER = new NativeLoader("org.lwjgl.harfbuzz", "harfbuzz",
            "hb_blob_create",
            "hb_blob_destroy",
            "hb_face_count",
            "hb_face_create",
            "hb_face_destroy",
            "hb_face_get_glyph_count",
            "hb_font_create",
            "hb_font_destroy",
            "hb_font_set_scale",
            "hb_font_get_nominal_glyph",
            "hb_buffer_create",
            "hb_buffer_destroy",
            "hb_buffer_clear_contents",
            "hb_buffer_set_direction",
            "hb_buffer_set_script",
            "hb_buffer_set_language",
            "hb_buffer_add_utf16",
            "hb_buffer_guess_segment_properties",
            "hb_buffer_allocation_successful",
            "hb_buffer_get_glyph_infos",
            "hb_buffer_get_glyph_positions",
            "hb_shape",
            "hb_language_from_string",
            "hb_feature_from_string");

    private static long blobCreate;
    private static long blobDestroy;
    private static long faceCount;
    private static long faceCreate;
    private static long faceDestroy;
    private static long faceGetGlyphCount;
    private static long fontCreate;
    private static long fontDestroy;
    private static long fontSetScale;
    private static long fontGetNominalGlyph;
    private static long bufferCreate;
    private static long bufferDestroy;
    private static long bufferClearContents;
    private static long bufferSetDirection;
    private static long bufferSetScript;
    private static long bufferSetLanguage;
    private static long bufferAddUtf16;
    private static long bufferGuessSegmentProperties;
    private static long bufferAllocationSuccessful;
    private static long bufferGetGlyphInfos;
    private static long bufferGetGlyphPositions;
    private static long shape;
    private static long languageFromString;
    private static long featureFromString;

    static {
        if (LOADER.available()) {
            blobCreate = LOADER.function("hb_blob_create");
            blobDestroy = LOADER.function("hb_blob_destroy");
            faceCount = LOADER.function("hb_face_count");
            faceCreate = LOADER.function("hb_face_create");
            faceDestroy = LOADER.function("hb_face_destroy");
            faceGetGlyphCount = LOADER.function("hb_face_get_glyph_count");
            fontCreate = LOADER.function("hb_font_create");
            fontDestroy = LOADER.function("hb_font_destroy");
            fontSetScale = LOADER.function("hb_font_set_scale");
            fontGetNominalGlyph = LOADER.function("hb_font_get_nominal_glyph");
            bufferCreate = LOADER.function("hb_buffer_create");
            bufferDestroy = LOADER.function("hb_buffer_destroy");
            bufferClearContents = LOADER.function("hb_buffer_clear_contents");
            bufferSetDirection = LOADER.function("hb_buffer_set_direction");
            bufferSetScript = LOADER.function("hb_buffer_set_script");
            bufferSetLanguage = LOADER.function("hb_buffer_set_language");
            bufferAddUtf16 = LOADER.function("hb_buffer_add_utf16");
            bufferGuessSegmentProperties = LOADER.function("hb_buffer_guess_segment_properties");
            bufferAllocationSuccessful = LOADER.function("hb_buffer_allocation_successful");
            bufferGetGlyphInfos = LOADER.function("hb_buffer_get_glyph_infos");
            bufferGetGlyphPositions = LOADER.function("hb_buffer_get_glyph_positions");
            shape = LOADER.function("hb_shape");
            languageFromString = LOADER.function("hb_language_from_string");
            featureFromString = LOADER.function("hb_feature_from_string");
        }
    }

    private HarfBuzzNative() {
    }

    public static boolean available() {
        return LOADER.available();
    }

    public static String status() {
        return LOADER.status();
    }

    public static long hbBlobCreate(long data, int length, int mode, long userData, long destroy) {
        return invokePPPP(data, length, mode, userData, destroy, blobCreate);
    }

    public static void hbBlobDestroy(long blob) {
        invokePV(blob, blobDestroy);
    }

    public static int hbFaceCount(long blob) {
        return invokePI(blob, faceCount);
    }

    public static long hbFaceCreate(long blob, int index) {
        return invokePP(blob, index, faceCreate);
    }

    public static void hbFaceDestroy(long face) {
        invokePV(face, faceDestroy);
    }

    public static int hbFaceGetGlyphCount(long face) {
        return invokePI(face, faceGetGlyphCount);
    }

    public static long hbFontCreate(long face) {
        return invokePP(face, fontCreate);
    }

    public static void hbFontDestroy(long font) {
        invokePV(font, fontDestroy);
    }

    public static void hbFontSetScale(long font, int xScale, int yScale) {
        invokePV(font, xScale, yScale, fontSetScale);
    }

    public static boolean hbFontGetNominalGlyph(long font, int codePoint, long glyphPointer) {
        return invokePPI(font, codePoint, glyphPointer, fontGetNominalGlyph) != 0;
    }

    public static long hbBufferCreate() {
        return invokeP(bufferCreate);
    }

    public static void hbBufferDestroy(long buffer) {
        invokePV(buffer, bufferDestroy);
    }

    public static void hbBufferClearContents(long buffer) {
        invokePV(buffer, bufferClearContents);
    }

    public static void hbBufferSetDirection(long buffer, int direction) {
        invokePV(buffer, direction, bufferSetDirection);
    }

    public static void hbBufferSetScript(long buffer, int script) {
        invokePV(buffer, script, bufferSetScript);
    }

    public static void hbBufferSetLanguage(long buffer, long language) {
        invokePPV(buffer, language, bufferSetLanguage);
    }

    public static void hbBufferAddUtf16(long buffer, long text, int textLength, int itemOffset, int itemLength) {
        invokePPPPPV(buffer, text, textLength, itemOffset, itemLength, bufferAddUtf16);
    }

    public static void hbBufferGuessSegmentProperties(long buffer) {
        invokePV(buffer, bufferGuessSegmentProperties);
    }

    public static boolean hbBufferAllocationSuccessful(long buffer) {
        return invokePI(buffer, bufferAllocationSuccessful) != 0;
    }

    public static long hbBufferGetGlyphInfos(long buffer, long lengthPointer) {
        return invokePPP(buffer, lengthPointer, bufferGetGlyphInfos);
    }

    public static long hbBufferGetGlyphPositions(long buffer, long lengthPointer) {
        return invokePPP(buffer, lengthPointer, bufferGetGlyphPositions);
    }

    public static void hbShape(long font, long buffer, long features, int featureCount) {
        invokePPPV(font, buffer, features, featureCount, shape);
    }

    public static long hbLanguageFromString(long string, int length) {
        return invokePP(string, length, languageFromString);
    }

    public static boolean hbFeatureFromString(long string, int length, long featurePointer) {
        return invokePPI(string, length, featurePointer, featureFromString) != 0;
    }
}
