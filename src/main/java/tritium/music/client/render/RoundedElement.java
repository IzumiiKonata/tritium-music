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

public record RoundedElement(
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
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build();

    public RoundedElement(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            List<Vertex> vertices,
            float width,
            float height,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(pipeline, textureSetup, new Matrix3x2f(pose), vertices, scissorArea, computeBounds(width, height, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (Vertex vertex : vertices) {
            consumer.addVertexWith2DPose(pose, vertex.x(), vertex.y())
                    .setUv(vertex.u(), vertex.v())
                    .setColor(vertex.color())
                    .setLineWidth(vertex.radius());
        }
    }

    private static ScreenRectangle computeBounds(float width, float height, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle rectangle = new ScreenRectangle(0, 0, (int) Math.ceil(width), (int) Math.ceil(height)).transformMaxBounds(pose);
        return scissorArea == null ? rectangle : scissorArea.intersection(rectangle);
    }

    public record Vertex(float x, float y, float u, float v, int color, float radius) {
    }
}
