package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
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

    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
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
