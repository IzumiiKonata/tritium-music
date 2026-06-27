package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record MeshElement(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2fc pose,
        List<Vertex> vertices,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    public record Vertex(float x, float y, float u, float v, int color) {
    }

    public MeshElement(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            List<Vertex> vertices,
            float x0,
            float y0,
            float x1,
            float y1,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(pipeline, textureSetup, new Matrix3x2f(pose), vertices, scissorArea, computeBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(final VertexConsumer consumer) {
        for (Vertex vertex : vertices) {
            consumer.addVertexWith2DPose(this.pose, vertex.x(), vertex.y())
                    .setUv(vertex.u(), vertex.v())
                    .setColor(vertex.color());
        }
    }

    private static ScreenRectangle computeBounds(
            float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea
    ) {
        int ix = (int) Math.floor(Math.min(x0, x1));
        int iy = (int) Math.floor(Math.min(y0, y1));
        int iw = (int) Math.ceil(Math.abs(x1 - x0));
        int ih = (int) Math.ceil(Math.abs(y1 - y0));
        ScreenRectangle rect = new ScreenRectangle(ix, iy, iw, ih).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rect) : rect;
    }
}
