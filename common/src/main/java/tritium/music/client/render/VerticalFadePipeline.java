package tritium.music.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class VerticalFadePipeline {

    public static final RenderPipeline PIPELINE = net.minecraft.client.renderer.RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("tritium-music", "pipeline/vertical_fade"))
            .withVertexShader(Identifier.fromNamespaceAndPath("tritium-music", "core/vertical_fade"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("tritium-music", "core/vertical_fade"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build());

    public static void initialize() {
    }

    private VerticalFadePipeline() {
    }
}
