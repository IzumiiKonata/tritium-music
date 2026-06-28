package tritium.music.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

public class MusicRenderTarget {

    private final String label;
    private @Nullable TextureTarget target;
    private int width = -1, height = -1;

    public MusicRenderTarget(String label) {
        this.label = label;
    }

    public TextureTarget ensure(int width, int height) {
        RenderSystem.assertOnRenderThread();
        if (target == null || this.width != width || this.height != height) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(label, width, height, false, GpuFormat.RGBA8_UNORM);
            this.width = width;
            this.height = height;
        }
        return target;
    }

    public @Nullable GpuTextureView colorTextureView() {
        return target != null ? target.getColorTextureView() : null;
    }

    public @Nullable TextureTarget target() {
        return target;
    }

    public int width() { return width; }
    public int height() { return height; }

    public void destroy() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
            width = height = -1;
        }
    }
}
