package tritium.music.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import tritium.music.platform.MusicPlatform;
import tritium.music.platform.TextureHandle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class FabricMusicPlatform implements MusicPlatform {

    private final java.util.concurrent.ExecutorService executor;
    private final Set<TextureHandle> uploaded = ConcurrentHashMap.newKeySet();

    public FabricMusicPlatform() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "tritium-music-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newThreadPerTaskExecutor(factory);
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
        textureManager.release(id);
        textureManager.register(id, new DynamicTexture(id::toString, nativeImage));
        uploaded.add(handle);
    }

    @Override
    public boolean hasTexture(TextureHandle handle) {
        return uploaded.contains(handle);
    }

    @Override
    public void deleteTexture(TextureHandle handle) {
        mc().getTextureManager().release(toIdentifier(handle));
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

    private static NativeImage toNativeImage(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
        } catch (IOException e) {
            return null;
        }
    }

    private static String stripFormatting(String message) {
        return message.replaceAll("§.", "");
    }
}
