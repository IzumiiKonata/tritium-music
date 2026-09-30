package tritium.music.client.rendering.font.binding;

import static org.lwjgl.system.JNI.invokePI;
import static org.lwjgl.system.JNI.invokePP;
import static org.lwjgl.system.JNI.invokePPPPPI;

public final class FreeTypeNative {

    public static final int FT_ERR_OK = 0;

    private static final NativeLoader LOADER = new NativeLoader("org.lwjgl.freetype", "freetype",
            "FT_Init_FreeType",
            "FT_New_Memory_Face",
            "FT_Done_Face",
            "FT_Done_FreeType",
            "FT_Get_Postscript_Name");

    private static long initFreeType;
    private static long newMemoryFace;
    private static long doneFace;
    private static long doneFreeType;
    private static long getPostscriptName;

    static {
        if (LOADER.available()) {
            initFreeType = LOADER.function("FT_Init_FreeType");
            newMemoryFace = LOADER.function("FT_New_Memory_Face");
            doneFace = LOADER.function("FT_Done_Face");
            doneFreeType = LOADER.function("FT_Done_FreeType");
            getPostscriptName = LOADER.function("FT_Get_Postscript_Name");
        }
    }

    private FreeTypeNative() {
    }

    public static boolean available() {
        return LOADER.available();
    }

    public static String status() {
        return LOADER.status();
    }

    public static int initFreeType(long libraryPointer) {
        return invokePI(libraryPointer, initFreeType);
    }

    public static int newMemoryFace(long library, long base, long size, long faceIndex, long facePointer) {
        return invokePPPPPI(library, base, size, faceIndex, facePointer, newMemoryFace);
    }

    public static int doneFace(long face) {
        return invokePI(face, doneFace);
    }

    public static int doneFreeType(long library) {
        return invokePI(library, doneFreeType);
    }

    public static long postScriptNameAddress(long face) {
        return invokePP(face, getPostscriptName);
    }
}
