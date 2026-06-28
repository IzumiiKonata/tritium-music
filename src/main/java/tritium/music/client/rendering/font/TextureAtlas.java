package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import tritium.music.core.util.AsyncUtil;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TextureAtlas {

    private static final int ATLAS_SIZE = 2048;
    private static final int PADDING = 2;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final Set<TextureAtlas> LIVE_ATLASES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void flushAllDirty() {
        for (TextureAtlas atlas : LIVE_ATLASES) {
            atlas.flush();
        }
    }

    private final Identifier identifier;
    private DynamicTexture texture;
    private NativeImage image;

    private int currentX = PADDING;
    private int currentY = PADDING;
    private int currentRowHeight = 0;

    private final List<AtlasRegion> regions = new ArrayList<>();

    private volatile boolean dirty = false;
    private volatile boolean flushScheduled = false;

    public TextureAtlas() {
        this.identifier = Identifier.fromNamespaceAndPath("tritium-music", "font/atlas_" + COUNTER.getAndIncrement());
        LIVE_ATLASES.add(this);
        this.init();
    }

    public void init() {
        AsyncUtil.runOnRenderThread(() -> {
            this.image = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            this.texture = new DynamicTexture(this.identifier::toString, this.image);
            Minecraft.getInstance().getTextureManager().register(this.identifier, this.texture);
        });
    }

    public Identifier identifier() {
        return identifier;
    }

    public NativeImage getImage() {
        return image;
    }

    public AtlasRegion upload(BufferedImage glyph) {
        int width = glyph.getWidth();
        int height = glyph.getHeight();

        if (currentX + width + PADDING > ATLAS_SIZE) {
            currentX = PADDING;
            currentY += currentRowHeight + PADDING;
            currentRowHeight = 0;
        }

        if (currentY + height + PADDING > ATLAS_SIZE) {
            return null;
        }

        if (image == null || texture == null) {
            return null;
        }

        int originX = currentX;
        int originY = currentY;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (glyph.getRGB(x, y) >> 24) & 0xFF;
                image.setPixelABGR(originX + x, originY + y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        dirty = true;

        float u0 = (float) currentX / ATLAS_SIZE;
        float v0 = (float) currentY / ATLAS_SIZE;
        float u1 = (float) (currentX + width) / ATLAS_SIZE;
        float v1 = (float) (currentY + height) / ATLAS_SIZE;

        AtlasRegion region = new AtlasRegion(u0, v0, u1, v1, width, height);
        regions.add(region);

        currentX += width + PADDING;
        currentRowHeight = Math.max(currentRowHeight, height);

        return region;
    }

    public void scheduleFlush() {
        if (!flushScheduled) {
            flushScheduled = true;
            AsyncUtil.runOnRenderThread(this::flush);
        }
    }

    public void flush() {
        flushScheduled = false;
        if (dirty && texture != null) {
            dirty = false;
            texture.upload();
        }
    }

    public void destroy() {
        LIVE_ATLASES.remove(this);
        AsyncUtil.runOnRenderThread(() -> {
            Minecraft.getInstance().getTextureManager().release(identifier);
            texture = null;
            image = null;
        });
        currentX = PADDING;
        currentY = PADDING;
        currentRowHeight = 0;
        regions.clear();
    }

    public static class AtlasRegion {
        public final float u0, v0, u1, v1;
        public final int width, height;

        public AtlasRegion(float u0, float v0, float u1, float v1, int width, int height) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
        }
    }
}
