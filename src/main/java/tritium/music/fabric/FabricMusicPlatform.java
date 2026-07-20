package tritium.music.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import tritium.music.platform.MusicPlatform;
import tritium.music.platform.TextureHandle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class FabricMusicPlatform implements MusicPlatform {

    private final java.util.concurrent.ExecutorService executor;
    private final Set<TextureHandle> uploaded = ConcurrentHashMap.newKeySet();
    private final Map<Identifier, DynamicTexture> textureCache = new ConcurrentHashMap<>();
    private final Map<Identifier, NativeImage> imageCache = new ConcurrentHashMap<>();

    public FabricMusicPlatform() {
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("tritium-music-worker-", 1).factory());
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    private Identifier toIdentifier(TextureHandle handle) {
        return Identifier.fromNamespaceAndPath(handle.getNamespace(), handle.getPath());
    }

    @Override
    public File configDir() {
        File dir = new File(mc().gameDirectory, "tritium-music");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @Override
    public void runAsync(Runnable task) {
        executor.submit(task);
    }

    @Override
    public void runOnRenderThread(Runnable task) {
        mc().execute(task);
    }

    @Override
    public void uploadTexture(TextureHandle handle, BufferedImage image) {
        if (image == null) {
            return;
        }

        NativeImage nativeImage = toNativeImage(image);
        if (nativeImage == null) {
            return;
        }

        Identifier id = toIdentifier(handle);
        TextureManager textureManager = mc().getTextureManager();

        DynamicTexture existing = textureCache.get(id);
        NativeImage existingImage = imageCache.get(id);

        if (existing != null && existingImage != null
                && existingImage.getWidth() == nativeImage.getWidth()
                && existingImage.getHeight() == nativeImage.getHeight()) {
            copyPixels(nativeImage, existingImage);
            nativeImage.close();
            existing.upload();
        } else {
            if (existing != null) {
                textureManager.release(id);
            }
            DynamicTexture dt = new DynamicTexture(id::toString, nativeImage);
            textureManager.register(id, dt);
            textureCache.put(id, dt);
            imageCache.put(id, nativeImage);
        }

        uploaded.add(handle);
    }

    @Override
    public boolean hasTexture(TextureHandle handle) {
        return uploaded.contains(handle);
    }

    @Override
    public void deleteTexture(TextureHandle handle) {
        Identifier id = toIdentifier(handle);
        mc().getTextureManager().release(id);
        textureCache.remove(id);
        imageCache.remove(id);
        uploaded.remove(handle);
    }

    @Override
    public void sendChatMessage(String message) {
        runOnRenderThread(() -> {
            if (mc().player != null) {
                mc().player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    @Override
    public void log(String message) {
        TritiumMusicMod.LOGGER.info(stripFormatting(message));
    }

    private static void copyPixels(NativeImage src, NativeImage dst) {
        int w = Math.min(src.getWidth(), dst.getWidth());
        int h = Math.min(src.getHeight(), dst.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setPixel(x, y, src.getPixel(x, y));
            }
        }
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                ni.setPixelABGR(x, y, abgr);
            }
        }
        return ni;
    }

    private static String stripFormatting(String message) {
        return message.replaceAll("§.", "");
    }
}
