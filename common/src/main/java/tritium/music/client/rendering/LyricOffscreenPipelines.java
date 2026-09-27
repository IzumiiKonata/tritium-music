package tritium.music.client.rendering;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LyricOffscreenPipelines {

    private static final BindGroupLayout SAMPLER = BindGroupLayout.builder()
            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    public static final RenderPipeline MASK = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/lyric_offscreen_mask"))
            .withVertexShader(id("core/lyric_offscreen_mask"))
            .withFragmentShader(id("core/lyric_offscreen_mask"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build());

    public static final RenderPipeline GLYPH = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/lyric_offscreen_glyph"))
            .withVertexShader(id("core/lyric_offscreen_glyph"))
            .withFragmentShader(id("core/lyric_offscreen_glyph"))
            .withBindGroupLayout(SAMPLER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build());

    private LyricOffscreenPipelines() {
    }

    public static void initialize() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }
}
