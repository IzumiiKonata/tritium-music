package tritium.music.client.rendering.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class PostEffectRenderer {

    private static TextureTarget source;
    private static TextureTarget scratch;
    private static TextureTarget output;
    private static final List<GpuBuffer> RETIRED_UNIFORMS = new ArrayList<>();

    private PostEffectRenderer() {
    }

    public static void render() {
        List<EffectQueue.Region> blurs = EffectQueue.blurs();
        List<EffectQueue.Region> blooms = EffectQueue.blooms();
        try {
            if (blurs.isEmpty() && blooms.isEmpty()) {
                retireUniforms();
                return;
            }

            RenderSystem.assertOnRenderThread();
            retireUniforms();
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            ensureTargets(main.width, main.height);

            if (!blurs.isEmpty()) {
                copy(main, source);
                gaussian(source, scratch, 5f, 0.5f, 1f, 0f);
                gaussian(scratch, output, 5f, 0.5f, 0f, 1f);
                for (EffectQueue.Region region : blurs) {
                    compositeBlur(main, region, minecraft.getWindow().getGuiScale());
                }
            }

            for (EffectQueue.Region region : blooms) {
                renderBloom(main, region, minecraft.getWindow().getGuiScale());
            }
        } finally {
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

    private static void gaussian(RenderTarget from, RenderTarget to, float radius, float stepWidth, float dx, float dy) {
        GpuBuffer uniform = blurUniform(dx, dy, radius, stepWidth);
        try (RenderPass pass = pass("Tritium gaussian", to)) {
            pass.setPipeline(EffectPipelines.GAUSSIAN);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", from.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("BlurInfo", uniform);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void compositeBlur(RenderTarget main, EffectQueue.Region region, int guiScale) {
        GpuBuffer uniform = effectUniform(region.alpha());
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
        GpuBuffer shape = shapeUniform(region, guiScale, main.height);
        try (RenderPass pass = pass("Tritium bloom mask", source)) {
            pass.setPipeline(EffectPipelines.BLOOM_MASK);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("ShapeInfo", shape);
            pass.draw(3, 1, 0, 0);
        }
        gaussian(source, scratch, 12f, 2f, 1f, 0f);
        gaussian(scratch, output, 12f, 2f, 0f, 1f);
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

    private static GpuBuffer blurUniform(float dx, float dy, float radius, float stepWidth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, 16)
                    .putVec2(dx, dy)
                    .putFloat(radius)
                    .putFloat(stepWidth);
            return uniform("Tritium blur info", builder);
        }
    }

    private static GpuBuffer effectUniform(float opacity) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return uniform("Tritium effect info", Std140Builder.onStack(stack, 16).putFloat(opacity));
        }
    }

    private static GpuBuffer shapeUniform(EffectQueue.Region region, int scale, int targetHeight) {
        float x = region.x() * scale;
        float y = targetHeight - (region.y() + region.height()) * scale;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, 32)
                    .putVec4(x, y, region.width() * scale, region.height() * scale)
                    .putFloat(region.radius() * scale)
                    .putFloat(region.alpha());
            return uniform("Tritium shape info", builder);
        }
    }

    private static GpuBuffer uniform(String label, Std140Builder builder) {
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label, 128, builder.get());
        RETIRED_UNIFORMS.add(buffer);
        return buffer;
    }

    private static void retireUniforms() {
        RETIRED_UNIFORMS.forEach(GpuBuffer::close);
        RETIRED_UNIFORMS.clear();
    }
}
