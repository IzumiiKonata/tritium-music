package tritium.music.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ClipPipeline {

    public static final RenderPipeline SOLID = create("clipped", false, PrimitiveTopology.QUADS);
    public static final RenderPipeline TEXTURED = create("clipped_texture", true, PrimitiveTopology.QUADS);
    public static final RenderPipeline LINES = create("clipped", false, PrimitiveTopology.DEBUG_LINES);

    public static void initialize() {
    }

    private static RenderPipeline create(String name, boolean textured, PrimitiveTopology topology) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/" + name + "_" + topology.name().toLowerCase()))
                .withVertexShader(id("core/" + name))
                .withFragmentShader(id("core/" + name))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, ClipElement.FORMAT)
                .withPrimitiveTopology(topology)
                .withCull(false);
        if (textured) {
            builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
        }
        return RenderPipelines.register(builder.build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }

    private ClipPipeline() {
    }
}
