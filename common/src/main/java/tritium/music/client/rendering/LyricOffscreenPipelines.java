package tritium.music.client.rendering;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LyricOffscreenPipelines {

//    private static final BindGroupLayout SAMPLER = BindGroupLayout.builder()
//            .withSampler("Sampler0")
//            .build();

    public static final RenderPipeline MASK = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/lyric_offscreen_mask"))
            .withVertexShader(id("core/lyric_offscreen_mask"))
            .withFragmentShader(id("core/lyric_offscreen_mask"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
//            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
//            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build());

    public static final RenderPipeline GLYPH = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/lyric_offscreen_glyph"))
            .withVertexShader(id("core/lyric_offscreen_glyph"))
            .withFragmentShader(id("core/lyric_offscreen_glyph"))
//            .withBindGroupLayout(SAMPLER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
//            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
//            .withPrimitiveTopology(PrimitiveTopology.QUADS)
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
