package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class RoundedPipeline {

    public static final RenderPipeline SOLID = create("rounded", false);
    public static final RenderPipeline GRADIENT = create("rounded_gradient", false);
    public static final RenderPipeline TEXTURED = create("rounded_texture", true);
    public static final RenderPipeline OUTLINE = create("rounded_outline", false);
    public static final RenderPipeline OUTLINE_GRADIENT = create("rounded_outline_gradient", false);

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
                .withVertexFormat(RoundedElement.FORMAT, VertexFormat.Mode.QUADS)
//                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
//                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
//                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//                .withVertexBinding(0, RoundedElement.FORMAT)
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

    private RoundedPipeline() {
    }
}
