package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

public final class VerticalFadePipeline {

    public static final RenderPipeline PIPELINE = net.minecraft.client.renderer.RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("tritium-music", "pipeline/vertical_fade"))
            .withVertexShader(Identifier.fromNamespaceAndPath("tritium-music", "core/vertical_fade"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("tritium-music", "core/vertical_fade"))
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
//            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
//            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
//            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
//            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
//            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build());

    public static void initialize() {
    }

    private VerticalFadePipeline() {
    }
}
