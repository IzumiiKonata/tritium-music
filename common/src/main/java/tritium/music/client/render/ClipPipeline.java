package tritium.music.client.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
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
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, ClipElement.FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
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
