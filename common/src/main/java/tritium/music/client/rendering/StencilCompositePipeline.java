package tritium.music.client.rendering;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

public final class StencilCompositePipeline {

    public static final RenderPipeline PIPELINE = net.minecraft.client.renderer.RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("tritium-music", "pipeline/stencil_composite"))
            .withVertexShader(Identifier.fromNamespaceAndPath("tritium-music", "core/stencil_composite"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("tritium-music", "core/stencil_composite"))
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
//            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
//            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
//            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
//            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
//            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build());

    public static void initialize() {
    }

    private StencilCompositePipeline() {
    }
}
