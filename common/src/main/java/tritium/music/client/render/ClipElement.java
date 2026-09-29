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
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ClipElement(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2fc pose,
        List<Vertex> vertices,
        ClipRect clip,
        int radiusFixed,
        int controlFixed,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    private static final float RADIUS_STEP = 32.0f;
    private static final float CONTROL_STEP = 1024.0f;

    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    public static ClipElement clipped(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                      List<Vertex> vertices, ClipRect clip,
                                      float x0, float y0, float x1, float y1,
                                      @Nullable ScreenRectangle scissorArea) {
        return create(pipeline, textureSetup, pose, vertices, clip, 0, 0, x0, y0, x1, y1, scissorArea);
    }

    public static ClipElement rounded(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                      List<Vertex> vertices, ClipRect clip, float radius,
                                      float width, float height,
                                      @Nullable ScreenRectangle scissorArea) {
        return create(pipeline, textureSetup, pose, vertices, clip, encode(radius * RADIUS_STEP), 0,
                0f, 0f, width, height, scissorArea);
    }

    public static ClipElement faded(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                    List<Vertex> vertices, ClipRect clip, float controlPercent,
                                    float x0, float y0, float x1, float y1,
                                    @Nullable ScreenRectangle scissorArea) {
        return create(pipeline, textureSetup, pose, vertices, clip, 0, encode(controlPercent * CONTROL_STEP),
                x0, y0, x1, y1, scissorArea);
    }

    private static ClipElement create(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                      List<Vertex> vertices, ClipRect clip, int radiusFixed, int controlFixed,
                                      float x0, float y0, float x1, float y1,
                                      @Nullable ScreenRectangle scissorArea) {
        return new ClipElement(pipeline, textureSetup, new Matrix3x2f(pose), vertices, clip, radiusFixed, controlFixed,
                scissorArea, MeshElement.bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    private static int encode(float value) {
        int rounded = Math.round(value);
        return rounded < Short.MIN_VALUE ? Short.MIN_VALUE : Math.min(rounded, Short.MAX_VALUE);
    }

    private static int encodeClip(float coordinate) {
        return Math.clamp(Math.round(coordinate * 8.0f), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        Vector2f position = new Vector2f();
        for (Vertex vertex : vertices) {
            pose.transformPosition(vertex.x(), vertex.y(), position);
            consumer.addVertex(position.x, position.y, clip.right())
                    .setUv(vertex.u(), vertex.v())
                    .setColor(vertex.color())
                    .setUv1(radiusFixed, controlFixed)
                    .setUv2(encodeClip(clip.left()), encodeClip(clip.top()))
                    .setLineWidth(clip.bottom());
        }
    }

    public record Vertex(float x, float y, float u, float v, int color) {
    }
}
