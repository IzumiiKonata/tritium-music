package tritium.music.client.audio;

import org.lwjgl.openal.ALC10;
import tritium.music.platform.Platform;

public final class OpenALContext {

    private static final ThreadLocal<Long> BOUND = ThreadLocal.withInitial(() -> 0L);

    private static volatile long context;
    private static volatile boolean reported;

    private OpenALContext() {
    }

    public static void set(long handle) {
        context = handle;
        BOUND.set(0L);
    }

    public static long handle() {
        return context;
    }

    public static void invalidate() {
        context = 0;
    }

    public static long current() {
        try {
            return ALC10.alcGetCurrentContext();
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public static boolean bind() {
        long target = context;
        if (target == 0) {
            return false;
        }
        if (BOUND.get() == target) {
            return true;
        }

        try {
            if (current() == target) {
                BOUND.set(target);
                return true;
            }
            boolean made = ALC10.alcMakeContextCurrent(target);
            if (made) {
                BOUND.set(target);
            }
            if (!reported) {
                reported = true;
                log("attached the Minecraft OpenAL context to the music thread (success=" + made + ")");
            }
            return made;
        } catch (Throwable throwable) {
            if (!reported) {
                reported = true;
                log("attaching the Minecraft OpenAL context failed: " + throwable);
            }
            return false;
        }
    }

    private static void log(String message) {
        try {
            Platform.log("[NCM] " + message);
        } catch (Throwable ignored) {
        }
    }
}
