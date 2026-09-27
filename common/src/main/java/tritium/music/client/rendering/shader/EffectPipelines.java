package tritium.music.client.rendering.shader;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class EffectPipelines {

    private static final BindGroupLayout BLUR_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("EffectInfo", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout SHAPE_LAYOUT = BindGroupLayout.builder()
            .withUniform("ShapeInfo", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout BLOOM_COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ShapeInfo", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline GAUSSIAN = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(id("pipeline/gaussian"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(id("post/gaussian"))
            .withBindGroupLayout(BLUR_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build());
    public static final RenderPipeline BLUR_COMPOSITE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(id("pipeline/blur_composite"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(id("post/blur_composite"))
            .withBindGroupLayout(COMPOSITE_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());
    public static final RenderPipeline BLOOM_MASK = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(id("pipeline/bloom_mask"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(id("post/bloom_mask"))
            .withBindGroupLayout(SHAPE_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());
    public static final RenderPipeline BLOOM_COMPOSITE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(id("pipeline/bloom_composite"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(id("post/bloom_composite"))
            .withBindGroupLayout(BLOOM_COMPOSITE_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());

    private EffectPipelines() {
    }

    public static void initialize() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }
}
