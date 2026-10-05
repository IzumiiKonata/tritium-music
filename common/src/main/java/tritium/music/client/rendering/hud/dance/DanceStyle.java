package tritium.music.client.rendering.hud.dance;

import lombok.Getter;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DanceStyle {

    private static final double DEFAULT_FRAME_DURATION_MS = 110;
    private static final double DEFAULT_BEATS_PER_CYCLE = 2.0;

    @Getter
    private final String id;
    @Getter
    private final String name;
    @Getter
    private final String pack;
    private final DanceFrameSource source;
    private final DanceStyleSpec spec;

    private final List<TextureHandle> textures = new ArrayList<>();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean loading;
    private volatile boolean loaded;
    private volatile boolean failed;
    private int frameWidth;
    private int frameHeight;

    DanceStyle(String id, DanceFrameSource source, DanceStyleSpec spec) {
        this.id = id;
        this.source = source;
        this.spec = spec;
        this.name = spec.name == null || spec.name.isBlank() ? id : spec.name;
        this.pack = spec.pack == null || spec.pack.isBlank() ? "" : spec.pack;
    }

    public String displayName() {
        return pack.isEmpty() ? name : pack + "/" + name;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isFailed() {
        return failed;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public double anchorX() {
        return clamp(spec.anchorX, 0.5, 0, 1);
    }

    public double anchorY() {
        return clamp(spec.anchorY, 0.95, 0, 1);
    }

    public double scale() {
        return clamp(spec.scale, 1, 0.05, 8);
    }

    public double opacity() {
        return clamp(spec.opacity, 1, 0, 1);
    }

    public double shadowWidth() {
        return clamp(spec.shadowWidth, 0.42, 0, 2);
    }

    public double shadowOffsetY() {
        return clamp(spec.shadowOffsetY, 0, -0.5, 0.5);
    }

    public double frameDurationMs() {
        return clamp(spec.frameDurationMs, DEFAULT_FRAME_DURATION_MS, 8, 5000);
    }

    public double beatsPerCycle() {
        return clamp(spec.beatsPerCycle, DEFAULT_BEATS_PER_CYCLE, 0.25, 16);
    }

    public double speed() {
        return clamp(spec.speed, 1, 0.05, 8);
    }

    public int frameCount() {
        return textures.size();
    }

    public TextureHandle frame(int index) {
        if (textures.isEmpty()) {
            return null;
        }
        return textures.get(Math.floorMod(index, textures.size()));
    }

    public void requestLoad() {
        if (loaded || loading) {
            return;
        }
        loading = true;
        int expected = generation.get();
        Platform.runAsync(() -> {
            List<BufferedImage> images;
            try {
                images = readFrames();
            } catch (Throwable throwable) {
                loading = false;
                failed = true;
                Platform.log("[NCM] Failed to load dance style " + id + ": " + throwable);
                return;
            }
            Platform.runOnRenderThread(() -> {
                if (generation.get() != expected) {
                    loading = false;
                    return;
                }
                try {
                    for (int index = 0; index < images.size(); index++) {
                        TextureHandle handle = TextureHandle.of("dance/" + id + "/" + index);
                        Platform.uploadTexture(handle, images.get(index));
                        textures.add(handle);
                    }
                    frameWidth = images.get(0).getWidth();
                    frameHeight = images.get(0).getHeight();
                    loaded = true;
                } catch (Throwable throwable) {
                    failed = true;
                    Platform.log("[NCM] Failed to upload dance style " + id + ": " + throwable);
                } finally {
                    loading = false;
                }
            });
        });
    }

    public void release() {
        generation.incrementAndGet();
        if (textures.isEmpty()) {
            loaded = false;
            return;
        }
        List<TextureHandle> released = new ArrayList<>(textures);
        textures.clear();
        loaded = false;
        Platform.runOnRenderThread(() -> released.forEach(Platform::deleteTexture));
    }

    private List<BufferedImage> readFrames() throws Exception {
        if (spec.atlas != null && spec.atlas.file != null && !spec.atlas.file.isBlank()) {
            return readAtlas(spec.atlas);
        }
        List<String> names = spec.frames != null && !spec.frames.isEmpty() ? spec.frames : source.frames();
        if (names.isEmpty()) {
            throw new IllegalStateException("no frames found for " + id);
        }
        List<BufferedImage> images = new ArrayList<>(names.size());
        for (String name : names) {
            BufferedImage image = read(name);
            if (image != null) {
                images.add(image);
            }
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("no readable frames for " + id);
        }
        return images;
    }

    private BufferedImage read(String name) throws Exception {
        try (InputStream stream = source.open(name)) {
            if (stream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            return image == null ? null : toArgb(image);
        }
    }

    private List<BufferedImage> readAtlas(DanceStyleSpec.Atlas atlas) throws Exception {
        int columns = atlas.columns == null || atlas.columns < 1 ? 1 : atlas.columns;
        int rows = atlas.rows == null || atlas.rows < 1 ? 1 : atlas.rows;
        int row = atlas.row == null ? 0 : Math.max(0, Math.min(rows - 1, atlas.row));
        int column = atlas.column == null ? 0 : atlas.column;

        BufferedImage sheet = read(atlas.file);
        if (sheet == null) {
            throw new IllegalStateException("unreadable atlas " + atlas.file);
        }
        int cellWidth = sheet.getWidth() / columns;
        int cellHeight = sheet.getHeight() / rows;
        int offsetX = atlas.x == null ? 0 : atlas.x;
        int offsetY = atlas.y == null ? 0 : atlas.y;
        int width = atlas.width == null || atlas.width <= 0 ? cellWidth - offsetX : atlas.width;
        int height = atlas.height == null || atlas.height <= 0 ? cellHeight - offsetY : atlas.height;

        int from = column > 0 ? column : 0;
        int to = column > 0 ? Math.min(columns, column + 1) : columns;
        List<BufferedImage> images = new ArrayList<>(to - from);
        for (int index = from; index < to; index++) {
            int x = index * cellWidth + offsetX;
            int y = row * cellHeight + offsetY;
            images.add(toArgb(sheet.getSubimage(
                    Math.max(0, Math.min(sheet.getWidth() - 1, x)),
                    Math.max(0, Math.min(sheet.getHeight() - 1, y)),
                    Math.max(1, Math.min(width, sheet.getWidth() - x)),
                    Math.max(1, Math.min(height, sheet.getHeight() - y)))));
        }
        return images;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static double clamp(Double value, double fallback, double min, double max) {
        if (value == null || !Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
