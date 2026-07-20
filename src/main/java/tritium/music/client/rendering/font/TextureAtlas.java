package tritium.music.client.rendering.font;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import tritium.music.core.util.AsyncUtil;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
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

    private final String identifierPath;
    private final List<Page> pages = new ArrayList<>();
    private volatile boolean flushScheduled = false;

    public TextureAtlas() {
        this.identifierPath = "font/atlas_" + COUNTER.getAndIncrement();
        LIVE_ATLASES.add(this);
    }

    public void init() {
        LIVE_ATLASES.add(this);
    }

    public Identifier identifier() {
        return pages.isEmpty() ? Identifier.fromNamespaceAndPath("tritium-music", identifierPath + "_0") : pages.getFirst().identifier;
    }

    public NativeImage getImage() {
        return pages.isEmpty() ? null : pages.getFirst().image;
    }

    public AtlasRegion upload(BufferedImage glyph) {
        if (pages.isEmpty()) {
            createPage();
        }

        for (Page page : pages) {
            AtlasRegion region = page.upload(glyph);
            if (region != null) {
                return region;
            }
        }

        return createPage().upload(glyph);
    }

    public void scheduleFlush() {
        if (!flushScheduled) {
            flushScheduled = true;
            AsyncUtil.runOnRenderThread(this::flush);
        }
    }

    public void flush() {
        flushScheduled = false;
        for (Page page : pages) {
            page.flush();
        }
    }

    public void destroy() {
        LIVE_ATLASES.remove(this);
        List<Page> oldPages = new ArrayList<>(pages);
        pages.clear();
        AsyncUtil.runOnRenderThread(() -> {
            for (Page page : oldPages) {
                Minecraft.getInstance().getTextureManager().release(page.identifier);
                page.texture = null;
                page.image = null;
            }
        });
    }

    private Page createPage() {
        Identifier identifier = Identifier.fromNamespaceAndPath("tritium-music", identifierPath + "_" + pages.size());
        NativeImage image = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
        DynamicTexture texture = new DynamicTexture(identifier::toString, image);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        Page page = new Page(identifier, texture, image);
        pages.add(page);
        return page;
    }

    private static class Page {
        private final Identifier identifier;
        private DynamicTexture texture;
        private NativeImage image;
        private int currentX = PADDING;
        private int currentY = PADDING;
        private int currentRowHeight;
        private boolean dirty;

        private Page(Identifier identifier, DynamicTexture texture, NativeImage image) {
            this.identifier = identifier;
            this.texture = texture;
            this.image = image;
        }

        private AtlasRegion upload(BufferedImage glyph) {
            int width = glyph.getWidth();
            int height = glyph.getHeight();
            if (width + PADDING * 2 > ATLAS_SIZE || height + PADDING * 2 > ATLAS_SIZE) {
                return null;
            }

            if (currentX + width + PADDING > ATLAS_SIZE) {
                currentX = PADDING;
                currentY += currentRowHeight + PADDING;
                currentRowHeight = 0;
            }
            if (currentY + height + PADDING > ATLAS_SIZE || image == null || texture == null) {
                return null;
            }

            int originX = currentX;
            int originY = currentY;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = glyph.getRGB(x, y) >>> 24;
                    image.setPixelABGR(originX + x, originY + y, (alpha << 24) | 0x00FFFFFF);
                }
            }

            currentX += width + PADDING;
            currentRowHeight = Math.max(currentRowHeight, height);
            dirty = true;
            return new AtlasRegion(
                    (float) originX / ATLAS_SIZE,
                    (float) originY / ATLAS_SIZE,
                    (float) (originX + width) / ATLAS_SIZE,
                    (float) (originY + height) / ATLAS_SIZE,
                    width, height, identifier, image
            );
        }

        private void flush() {
            if (dirty && texture != null) {
                dirty = false;
                texture.upload();
            }
        }
    }

    public static class AtlasRegion {
        public final float u0, v0, u1, v1;
        public final int width, height;
        public final Identifier identifier;
        public final NativeImage image;

        public AtlasRegion(float u0, float v0, float u1, float v1, int width, int height,
                           Identifier identifier, NativeImage image) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
            this.identifier = identifier;
            this.image = image;
        }
    }
}
