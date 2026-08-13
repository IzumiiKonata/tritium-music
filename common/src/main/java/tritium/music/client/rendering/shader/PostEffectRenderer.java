package tritium.music.client.rendering.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class PostEffectRenderer {

    private static TextureTarget source;
    private static TextureTarget scratch;
    private static TextureTarget output;
    private static DynamicUniformStorage<BlurInfo> blurUniforms;
    private static DynamicUniformStorage<EffectInfo> effectUniforms;
    private static DynamicUniformStorage<ShapeInfo> shapeUniforms;

    private PostEffectRenderer() {
    }

    public static void render() {
        List<EffectQueue.Region> blurs = EffectQueue.blurs();
        List<EffectQueue.Region> blooms = EffectQueue.blooms();
        try {
            if (blurs.isEmpty() && blooms.isEmpty()) {
                return;
            }

            RenderSystem.assertOnRenderThread();
            ensureUniforms();
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            ensureTargets(main.width, main.height);

            if (!blurs.isEmpty()) {
                copy(main, source);
                ScissorBounds blurBounds = bounds(blurs, minecraft.getWindow().getGuiScale(), main.width, main.height, 12);
                gaussian(source, scratch, 5f, 0.5f, 1f, 0f, blurBounds);
                gaussian(scratch, output, 5f, 0.5f, 0f, 1f, blurBounds);
                for (EffectQueue.Region region : blurs) {
                    compositeBlur(main, region, minecraft.getWindow().getGuiScale());
                }
            }

            for (EffectQueue.Region region : blooms) {
                renderBloom(main, region, minecraft.getWindow().getGuiScale());
            }
        } finally {
            endUniformFrame();
            EffectQueue.finishFrame();
        }
    }

    private static void ensureTargets(int width, int height) {
        if (source == null || source.width != width || source.height != height) {
            if (source != null) {
                source.destroyBuffers();
                scratch.destroyBuffers();
                output.destroyBuffers();
            }
            source = new TextureTarget("Tritium effect source", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
            scratch = new TextureTarget("Tritium effect scratch", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
            output = new TextureTarget("Tritium effect output", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
        }
    }

    private static void copy(RenderTarget from, RenderTarget to) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                from.getColorTexture(), to.getColorTexture(), 0, 0, 0, 0, 0, from.width, from.height
        );
    }

    private static void gaussian(RenderTarget from, RenderTarget to, float radius, float stepWidth, float dx, float dy,
                                 ScissorBounds bounds) {
        GpuBufferSlice uniform = blurUniforms.writeUniform(new BlurInfo(dx, dy, radius, stepWidth));
        try (RenderPass pass = pass("Tritium gaussian", to)) {
            pass.setPipeline(EffectPipelines.GAUSSIAN);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", from.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("BlurInfo", uniform);
            bounds.apply(pass);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void compositeBlur(RenderTarget main, EffectQueue.Region region, int guiScale) {
        GpuBufferSlice uniform = effectUniforms.writeUniform(new EffectInfo(region.alpha()));
        try (RenderPass pass = pass("Tritium blur composite", main)) {
            pass.setPipeline(EffectPipelines.BLUR_COMPOSITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", output.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("EffectInfo", uniform);
            applyScissor(pass, region, guiScale, main.width, main.height, 7);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void renderBloom(RenderTarget main, EffectQueue.Region region, int guiScale) {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(source.getColorTexture(), new Vector4f(0f));
        GpuBufferSlice shape = shapeUniform(region, guiScale, main.height);
        try (RenderPass pass = pass("Tritium bloom mask", source)) {
            pass.setPipeline(EffectPipelines.BLOOM_MASK);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("ShapeInfo", shape);
            bounds(List.of(region), guiScale, main.width, main.height, 1).apply(pass);
            pass.draw(3, 1, 0, 0);
        }
        ScissorBounds bloomBounds = bounds(List.of(region), guiScale, main.width, main.height, 74);
        gaussian(source, scratch, 12f, 2f, 1f, 0f, bloomBounds);
        gaussian(scratch, output, 12f, 2f, 0f, 1f, bloomBounds);
        try (RenderPass pass = pass("Tritium bloom composite", main)) {
            pass.setPipeline(EffectPipelines.BLOOM_COMPOSITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", output.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("ShapeInfo", shape);
            applyScissor(pass, region, guiScale, main.width, main.height, 50);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPass pass(String label, RenderTarget target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target.getColorTextureView(),
                Optional.empty(),
                null,
                OptionalDouble.empty()
        );
    }

    private static void applyScissor(RenderPass pass, EffectQueue.Region region, int scale, int targetWidth, int targetHeight, int padding) {
        int left = Math.max(0, (int) Math.floor(region.x() * scale) - padding);
        int top = Math.max(0, (int) Math.floor(region.y() * scale) - padding);
        int right = Math.min(targetWidth, (int) Math.ceil((region.x() + region.width()) * scale) + padding);
        int bottom = Math.min(targetHeight, (int) Math.ceil((region.y() + region.height()) * scale) + padding);
        if (right > left && bottom > top) {
            pass.enableScissor(left, targetHeight - bottom, right - left, bottom - top);
        }
    }

    private static ScissorBounds bounds(List<EffectQueue.Region> regions, int scale, int targetWidth, int targetHeight, int padding) {
        int left = targetWidth;
        int top = targetHeight;
        int right = 0;
        int bottom = 0;
        for (EffectQueue.Region region : regions) {
            left = Math.min(left, (int) Math.floor(region.x() * scale) - padding);
            top = Math.min(top, (int) Math.floor(region.y() * scale) - padding);
            right = Math.max(right, (int) Math.ceil((region.x() + region.width()) * scale) + padding);
            bottom = Math.max(bottom, (int) Math.ceil((region.y() + region.height()) * scale) + padding);
        }
        return new ScissorBounds(Math.max(0, left), Math.max(0, top), Math.min(targetWidth, right),
                Math.min(targetHeight, bottom), targetHeight);
    }

    private static GpuBufferSlice shapeUniform(EffectQueue.Region region, int scale, int targetHeight) {
        float x = region.x() * scale;
        float y = targetHeight - (region.y() + region.height()) * scale;
        return shapeUniforms.writeUniform(new ShapeInfo(x, y, region.width() * scale, region.height() * scale,
                region.radius() * scale, region.alpha()));
    }

    private static void ensureUniforms() {
        if (blurUniforms == null) {
            blurUniforms = new DynamicUniformStorage<>("Tritium blur info", 16, 8);
            effectUniforms = new DynamicUniformStorage<>("Tritium effect info", 16, 16);
            shapeUniforms = new DynamicUniformStorage<>("Tritium shape info", 32, 16);
        }
    }

    private static void endUniformFrame() {
        if (blurUniforms != null) {
            blurUniforms.endFrame();
            effectUniforms.endFrame();
            shapeUniforms.endFrame();
        }
    }

    private record BlurInfo(float dx, float dy, float radius, float stepWidth)
            implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec2(dx, dy).putFloat(radius).putFloat(stepWidth);
        }
    }

    private record EffectInfo(float opacity) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putFloat(opacity);
        }
    }

    private record ShapeInfo(float x, float y, float width, float height, float radius, float opacity)
            implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(x, y, width, height).putFloat(radius).putFloat(opacity);
        }
    }

    private record ScissorBounds(int left, int top, int right, int bottom, int targetHeight) {
        private void apply(RenderPass pass) {
            if (right > left && bottom > top) {
                pass.enableScissor(left, targetHeight - bottom, right - left, bottom - top);
            }
        }
    }
}
