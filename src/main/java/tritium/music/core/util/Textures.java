package tritium.music.core.util;

import lombok.experimental.UtilityClass;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@UtilityClass
public class Textures {

    public BufferedImage decode(InputStream stream) {
        try {
            return ImageIO.read(stream);
        } catch (IOException e) {
            return null;
        }
    }

    public void loadTexture(TextureHandle handle, BufferedImage image) {
        if (image == null) {
            return;
        }
        Platform.runOnRenderThread(() -> Platform.uploadTexture(handle, image));
    }

    public void loadTextureAsync(TextureHandle handle, BufferedImage image) {
        loadTexture(handle, image);
    }

    public void downloadTextureAndLoadAsync(String url, TextureHandle handle) {
        if (Platform.hasTexture(handle)) {
            return;
        }
        AsyncUtil.runAsync(() -> {
            try (InputStream stream = HttpUtils.downloadStream(url)) {
                if (stream != null) {
                    BufferedImage image = decode(stream);
                    if (image != null) {
                        loadTexture(handle, image);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
