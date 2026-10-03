package tritium.music.platform;

import javax.sound.sampled.AudioFormat;
import java.awt.image.BufferedImage;
import java.io.File;

public interface MusicPlatform {

    File configDir();

    void runAsync(Runnable task);

    void runOnRenderThread(Runnable task);

    PcmOutput openPcmOutput(AudioFormat format, int bufferBytes);

    void uploadTexture(TextureHandle handle, BufferedImage image);

    boolean hasTexture(TextureHandle handle);

    void deleteTexture(TextureHandle handle);

    void sendChatMessage(String message);

    String translate(String key, Object... arguments);

    String gameLanguage();

    void log(String message);
}
