package tritium.music.platform;

import java.awt.image.BufferedImage;
import java.io.File;

public interface MusicPlatform {

    File configDir();

    void runAsync(Runnable task);

    void runOnRenderThread(Runnable task);

    void uploadTexture(TextureHandle handle, BufferedImage image);

    boolean hasTexture(TextureHandle handle);

    void deleteTexture(TextureHandle handle);

    void sendChatMessage(String message);

    void log(String message);
}
