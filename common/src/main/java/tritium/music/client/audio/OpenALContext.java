package tritium.music.client.audio;

import org.lwjgl.openal.ALC10;
import tritium.music.platform.Platform;

public final class OpenALContext {

    private static final Object LOCK = new Object();

    private static long handle;
    private static long generation;

    private OpenALContext() {
    }

    public static long live() {
        try {
            return ALC10.alcGetCurrentContext();
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public static long generation() {
        long current = live();
        synchronized (LOCK) {
            if (current == 0) {
                return 0;
            }
            if (current != handle) {
                handle = current;
                generation++;
                log("bound to the Minecraft OpenAL context 0x" + Long.toHexString(current)
                        + " (generation " + generation + ")");
            }
            return generation;
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            handle = 0;
            generation++;
        }
    }

    private static void log(String message) {
        try {
            Platform.log("[NCM] " + message);
        } catch (Throwable ignored) {
        }
    }
}
