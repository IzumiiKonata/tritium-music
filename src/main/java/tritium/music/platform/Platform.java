package tritium.music.platform;

import java.awt.image.BufferedImage;
import java.io.File;

public final class Platform {

    private static MusicPlatform impl;

    private Platform() {
    }

    public static void set(MusicPlatform platform) {
        impl = platform;
    }

    public static MusicPlatform get() {
        if (impl == null) {
            throw new IllegalStateException("MusicPlatform has not been initialized");
        }
        return impl;
    }

    public static File configDir() {
        return get().configDir();
    }

    public static void runAsync(Runnable task) {
        get().runAsync(task);
    }

    public static void runOnRenderThread(Runnable task) {
        get().runOnRenderThread(task);
    }

    public static void uploadTexture(TextureHandle handle, BufferedImage image) {
        get().uploadTexture(handle, image);
    }

    public static boolean hasTexture(TextureHandle handle) {
        return get().hasTexture(handle);
    }

    public static void deleteTexture(TextureHandle handle) {
        get().deleteTexture(handle);
    }

    public static void sendChatMessage(String message) {
        get().sendChatMessage(message);
    }

    public static void log(String message) {
        get().log(message);
    }
}
