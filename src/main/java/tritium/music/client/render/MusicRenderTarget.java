package tritium.music.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;

/**
 * Thin wrapper around a Blaze3D offscreen render target. Reusable foundation
 * for multi-pass effects (blur/bloom). Effects are not yet wired up; this exists
 * so the infrastructure is in place and can be allocated lazily on the render thread.
 */
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

    public @Nullable TextureTarget target() {
        return target;
    }

    public void destroy() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
            width = height = -1;
        }
    }
}
