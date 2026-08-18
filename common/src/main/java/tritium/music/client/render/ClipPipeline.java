package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ClipPipeline {

    public static final RenderPipeline SOLID = create("clipped", false);
    public static final RenderPipeline TEXTURED = create("clipped_texture", true);

    public static void initialize() {
    }

    private static RenderPipeline create(String name, boolean textured) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/" + name))
                .withFragmentShader(id("core/" + name))
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
//                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(ClipElement.FORMAT, VertexFormat.Mode.QUADS)
//                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
        if (textured) {
            builder.withSampler("Sampler0");
//            builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
        }
        return RenderPipelines.register(builder.build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }

    private ClipPipeline() {
    }
}
