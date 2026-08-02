package tritium.music.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ClipElement(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2fc pose,
        List<Vertex> vertices,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    public ClipElement(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, List<Vertex> vertices,
                       float x0, float y0, float x1, float y1, @Nullable ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, new Matrix3x2f(pose), vertices, scissorArea,
                new MeshElement(pipeline, textureSetup, pose, List.of(), false, false,
                        x0, y0, x1, y1, scissorArea).bounds());
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (Vertex vertex : vertices) {
            consumer.addVertexWith2DPose(pose, vertex.x(), vertex.y())
                    .setUv(vertex.u(), vertex.v())
                    .setColor(vertex.color())
                    .setUv1(encode(vertex.clipLeft()), encode(vertex.clipTop()))
                    .setUv2(encode(vertex.clipRight()), encode(vertex.clipBottom()));
        }
    }

    static int encode(float coordinate) {
        return Math.clamp(Math.round(coordinate * 8.0f), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public record Vertex(float x, float y, float u, float v, int color,
                         float clipLeft, float clipTop, float clipRight, float clipBottom) {
    }
}
