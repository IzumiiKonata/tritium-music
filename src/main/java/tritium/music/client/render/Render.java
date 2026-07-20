package tritium.music.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import tritium.music.client.rendering.shader.EffectQueue;

import java.util.ArrayList;
import java.util.List;

public final class Render {

    private Render() {
    }

    private static GpuSampler linearSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    private static GuiRenderState state(GuiGraphicsExtractor g) {
        return g.guiRenderState;
    }

    private static @Nullable ScreenRectangle scissor(GuiGraphicsExtractor g) {
        return g.scissorStack.peek();
    }

    private static Matrix3x2f pose(GuiGraphicsExtractor g) {
        return new Matrix3x2f(g.pose());
    }

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        if (EffectQueue.captureRect(x, y, w, h, 0f, color)) {
            return;
        }
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        quad(verts, x, y, x + w, y + h, color);
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, x, y, x + w, y + h);
    }

    public static void gradientV(GuiGraphicsExtractor g, float x, float y, float w, float h, int top, int bottom) {
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, 0, 0, top));
        verts.add(new MeshElement.Vertex(x, y + h, 0, 0, bottom));
        verts.add(new MeshElement.Vertex(x + w, y + h, 0, 0, bottom));
        verts.add(new MeshElement.Vertex(x + w, y, 0, 0, top));
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, x, y, x + w, y + h);
    }

    public static void gradientH(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, 0, 0, left));
        verts.add(new MeshElement.Vertex(x, y + h, 0, 0, left));
        verts.add(new MeshElement.Vertex(x + w, y + h, 0, 0, right));
        verts.add(new MeshElement.Vertex(x + w, y, 0, 0, right));
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, x, y, x + w, y + h);
    }

    public static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        if (EffectQueue.captureRect(x, y, w, h, radius, color)) {
            return;
        }
        Dimensions dimensions = dimensions(x, y, w, h);
        submitRounded(g, RoundedPipeline.SOLID, TextureSetup.noTexture(), dimensions, radius,
                0f, 0f, 0f, 0f, color, color, color, color);
    }

    public static void roundedGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                       int bottomLeft, int topLeft, int bottomRight, int topRight) {
        Dimensions dimensions = dimensions(x, y, w, h);
        submitRounded(g, RoundedPipeline.GRADIENT, TextureSetup.noTexture(), dimensions, radius,
                0f, 0f, 0f, 0f, topLeft, bottomLeft, bottomRight, topRight);
    }

    public static void roundedOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                      float thickness, int color) {
        Dimensions dimensions = dimensions(x, y, w, h);
        submitRounded(g, RoundedPipeline.OUTLINE, TextureSetup.noTexture(), dimensions, radius - 2f,
                thickness, 0f, thickness, 0f, color, color, color, color);
    }

    public static void roundedOutlineGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                              float thickness, int bottomLeft, int topLeft, int bottomRight, int topRight) {
        Dimensions dimensions = dimensions(x, y, w, h);
        submitRounded(g, RoundedPipeline.OUTLINE_GRADIENT, TextureSetup.noTexture(), dimensions, radius - 2f,
                thickness, 0f, thickness, 0f, topLeft, bottomLeft, bottomRight, topRight);
    }

    public static void texture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float alpha) {
        texture(g, id, x, y, w, h, 0f, 0f, 1f, 1f, alpha);
    }

    public static void texture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h,
                               float u0, float v0, float u1, float v1, float alpha) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());

        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, u0, v0, color));
        verts.add(new MeshElement.Vertex(x, y + h, u0, v1, color));
        verts.add(new MeshElement.Vertex(x + w, y + h, u1, v1, color));
        verts.add(new MeshElement.Vertex(x + w, y, u1, v0, color));

        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, true, x, y, x + w, y + h);
    }

    public static void texturedQuad(GuiGraphicsExtractor g, Identifier id,
                                    float tlx, float tly, float blx, float bly, float brx, float bry, float trx, float trY,
                                    boolean flipX, float alpha) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());

        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;

        float u0 = flipX ? 1f : 0f, u1 = flipX ? 0f : 1f;
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(tlx, tly, u0, 0f, color));
        verts.add(new MeshElement.Vertex(blx, bly, u0, 1f, color));
        verts.add(new MeshElement.Vertex(brx, bry, u1, 1f, color));
        verts.add(new MeshElement.Vertex(trx, trY, u1, 0f, color));

        float minX = Math.min(Math.min(tlx, blx), Math.min(brx, trx));
        float minY = Math.min(Math.min(tly, bly), Math.min(bry, trY));
        float maxX = Math.max(Math.max(tlx, blx), Math.max(brx, trx));
        float maxY = Math.max(Math.max(tly, bly), Math.max(bry, trY));
        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, true, minX, minY, maxX, maxY);
    }

    public static void verticalFadeTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h,
                                           float controlPercent, float alpha) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());
        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, 0f, 0f, color, controlPercent, 0f, 0f));
        verts.add(new MeshElement.Vertex(x, y + h, 0f, 1f, color, controlPercent, 0f, 0f));
        verts.add(new MeshElement.Vertex(x + w, y + h, 1f, 1f, color, controlPercent, 0f, 0f));
        verts.add(new MeshElement.Vertex(x + w, y, 1f, 0f, color, controlPercent, 0f, 0f));
        submit(g, VerticalFadePipeline.PIPELINE, setup, verts, true, true, x, y, x + w, y + h);
    }

    public static void colorQuad(GuiGraphicsExtractor g,
                                 float tlx, float tly, float blx, float bly, float brx, float bry, float trx, float trY,
                                 int color) {
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(tlx, tly, 0, 0, color));
        verts.add(new MeshElement.Vertex(blx, bly, 0, 0, color));
        verts.add(new MeshElement.Vertex(brx, bry, 0, 0, color));
        verts.add(new MeshElement.Vertex(trx, trY, 0, 0, color));

        float minX = Math.min(Math.min(tlx, blx), Math.min(brx, trx));
        float minY = Math.min(Math.min(tly, bly), Math.min(bry, trY));
        float maxX = Math.max(Math.max(tlx, blx), Math.max(brx, trx));
        float maxY = Math.max(Math.max(tly, bly), Math.max(bry, trY));
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, minX, minY, maxX, maxY);
    }

    public static void glyph(GuiGraphicsExtractor g, Identifier atlas, float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1, int color) {
        glyph(g, atlas, x, y, w, h, u0, v0, u1, v1, color, color);
    }

    public static void glyph(GuiGraphicsExtractor g, Identifier atlas, float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1, int leftColor, int rightColor) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(atlas);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, u0, v0, leftColor));
        verts.add(new MeshElement.Vertex(x, y + h, u0, v1, leftColor));
        verts.add(new MeshElement.Vertex(x + w, y + h, u1, v1, rightColor));
        verts.add(new MeshElement.Vertex(x + w, y, u1, v0, rightColor));

        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, true, x, y, x + w, y + h);
    }

    public static void roundedTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha) {
        roundedTexture(g, id, x, y, w, h, radius, alpha, 0f, 0f, 1f, 1f);
    }

    public static void roundedTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha,
                                      float u0, float v0, float u1, float v1) {
        roundedTextureInternal(g, id, x, y, w, h, radius, alpha, u0, v0, u1, v1);
    }

    public static void roundedTextureSpecial(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha,
                                             float uOffset, float vOffset, float uScale, float vScale) {
        roundedTextureInternal(g, id, x, y, w, h, radius, alpha, uOffset, vOffset, uOffset + uScale, vOffset + vScale);
    }

    private static void roundedTextureInternal(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha,
                                               float u0, float v0, float u1, float v1) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());
        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;
        Dimensions dimensions = dimensions(x, y, w, h);
        if (dimensions.flipX()) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        if (dimensions.flipY()) {
            float swap = v0;
            v0 = v1;
            v1 = swap;
        }
        submitRounded(g, RoundedPipeline.TEXTURED, setup, dimensions, radius,
                u0, v0, u1, v1, color, color, color, color);
    }

    private static void submitRounded(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup, Dimensions dimensions,
                                      float radius, float u0, float v0, float u1, float v1,
                                      int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (dimensions.width() <= 0 || dimensions.height() <= 0) {
            return;
        }
        Matrix3x2f localPose = pose(g).translate(dimensions.x(), dimensions.y());
        List<RoundedElement.Vertex> vertices = new ArrayList<>(4);
        vertices.add(new RoundedElement.Vertex(0f, 0f, u0, v0, topLeft, radius));
        vertices.add(new RoundedElement.Vertex(0f, dimensions.height(), u0, v1, bottomLeft, radius));
        vertices.add(new RoundedElement.Vertex(dimensions.width(), dimensions.height(), u1, v1, bottomRight, radius));
        vertices.add(new RoundedElement.Vertex(dimensions.width(), 0f, u1, v0, topRight, radius));
        state(g).addGuiElement(new RoundedElement(
                pipeline, setup, localPose, vertices, dimensions.width(), dimensions.height(), scissor(g)
        ));
    }

    private static Dimensions dimensions(float x, float y, float width, float height) {
        boolean flipX = width < 0;
        boolean flipY = height < 0;
        if (flipX) {
            x += width;
            width = -width;
        }
        if (flipY) {
            y += height;
            height = -height;
        }
        return new Dimensions(x, y, width, height, flipX, flipY);
    }

    private record Dimensions(float x, float y, float width, float height, boolean flipX, boolean flipY) {
    }

    private static void quad(List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1, int color) {
        verts.add(new MeshElement.Vertex(x0, y0, 0, 0, color));
        verts.add(new MeshElement.Vertex(x0, y1, 0, 1, color));
        verts.add(new MeshElement.Vertex(x1, y1, 1, 1, color));
        verts.add(new MeshElement.Vertex(x1, y0, 1, 0, color));
    }

    private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup,
                               List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1) {
        submit(g, pipeline, setup, verts, false, false, x0, y0, x1, y1);
    }

    private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup,
                               List<MeshElement.Vertex> verts, boolean writeUv, float x0, float y0, float x1, float y1) {
        submit(g, pipeline, setup, verts, writeUv, false, x0, y0, x1, y1);
    }

    private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup,
                               List<MeshElement.Vertex> verts, boolean writeUv, boolean writeNormal,
                               float x0, float y0, float x1, float y1) {
        state(g).addGuiElement(new MeshElement(pipeline, setup, pose(g), verts, writeUv, writeNormal, x0, y0, x1, y1, scissor(g)));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
