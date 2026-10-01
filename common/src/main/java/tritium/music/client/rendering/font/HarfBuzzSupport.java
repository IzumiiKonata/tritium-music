package tritium.music.client.rendering.font;

import tritium.music.core.assets.AssetPlatform;
import tritium.music.platform.Platform;

public final class HarfBuzzSupport {

    private static final int UNKNOWN = 0;
    private static final int AVAILABLE = 1;
    private static final int UNAVAILABLE = 2;

    private static final String HARFBUZZ_CLASS = "org.lwjgl.util.harfbuzz.HarfBuzz";
    private static final String LIBRARY = "libharfbuzz.so";

    private static volatile int state = UNKNOWN;
    private static volatile String failure = "";

    private HarfBuzzSupport() {
    }

    public static boolean isAvailable() {
        return probe() == AVAILABLE;
    }

    public static String failure() {
        probe();
        return failure;
    }

    public static void markUnavailable(Throwable throwable) {
        synchronized (HarfBuzzSupport.class) {
            if (state == UNAVAILABLE) {
                return;
            }
            state = UNAVAILABLE;
            failure = describe(throwable);
        }
        report();
    }

    private static int probe() {
        int cached = state;
        if (cached != UNKNOWN) {
            return cached;
        }
        synchronized (HarfBuzzSupport.class) {
            if (state == UNKNOWN) {
                state = load();
            }
            return state;
        }
    }

    private static int load() {
        if (AssetPlatform.isAndroid()) {
            failure = "LWJGL ships no Android (bionic) build of " + LIBRARY
                    + ", the AWT layout engine is used instead";
            report();
            return UNAVAILABLE;
        }
        try {
            Class.forName(HARFBUZZ_CLASS, true, HarfBuzzSupport.class.getClassLoader());
            return AVAILABLE;
        } catch (Throwable throwable) {
            failure = describe(throwable);
            report();
            return UNAVAILABLE;
        }
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static void report() {
        try {
            Platform.log("[font] HarfBuzz is unavailable, shaping falls back to the AWT layout engine: " + failure);
        } catch (Throwable ignored) {
        }
    }
}
