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
        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        quad(verts, x, y, x + w, y + h, color);
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, x, y, x + w, y + h);
    }

    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) {
            return;
        }
        float nx = -dy / len * thickness * 0.5f;
        float ny = dx / len * thickness * 0.5f;

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x0 + nx, y0 + ny, 0, 0, color));
        verts.add(new MeshElement.Vertex(x0 - nx, y0 - ny, 0, 0, color));
        verts.add(new MeshElement.Vertex(x1 - nx, y1 - ny, 0, 0, color));
        verts.add(new MeshElement.Vertex(x1 + nx, y1 + ny, 0, 0, color));

        float minX = Math.min(x0, x1) - thickness, minY = Math.min(y0, y1) - thickness;
        float maxX = Math.max(x0, x1) + thickness, maxY = Math.max(y0, y1) + thickness;
        submit(g, RenderPipelines.GUI, TextureSetup.noTexture(), verts, minX, minY, maxX, maxY);
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
        float r = Math.min(radius, Math.min(w, h) / 2f);
        if (r <= 0.5f) {
            rect(g, x, y, w, h, color);
            return;
        }

        List<MeshElement.Vertex> verts = new ArrayList<>();
        float x1 = x + w, y1 = y + h;

        float aa = Math.min(1f, 1f / r);

        sdfQuad(verts, x + r, y, x1 - r, y1, color);
        sdfQuad(verts, x, y + r, x + r, y1 - r, color);
        sdfQuad(verts, x1 - r, y + r, x1, y1 - r, color);

        cornerQuad(verts, x, y, x + r, y + r, x + r, y + r, r, aa, color);
        cornerQuad(verts, x1 - r, y, x1, y + r, x1 - r, y + r, r, aa, color);
        cornerQuad(verts, x1 - r, y1 - r, x1, y1, x1 - r, y1 - r, r, aa, color);
        cornerQuad(verts, x, y1 - r, x + r, y1, x + r, y1 - r, r, aa, color);

        submit(g, RoundedPipeline.SOLID, TextureSetup.noTexture(), verts, true, x, y, x1, y1);
    }

    private static void sdfQuad(List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1, int color) {
        verts.add(new MeshElement.Vertex(x0, y0, 0, 0, color, 1f));
        verts.add(new MeshElement.Vertex(x0, y1, 0, 0, color, 1f));
        verts.add(new MeshElement.Vertex(x1, y1, 0, 0, color, 1f));
        verts.add(new MeshElement.Vertex(x1, y0, 0, 0, color, 1f));
    }

    private static void cornerQuad(List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1,
                                   float cx, float cy, float r, float aa, int color) {
        verts.add(new MeshElement.Vertex(x0, y0, (x0 - cx) / r, (y0 - cy) / r, color, aa));
        verts.add(new MeshElement.Vertex(x0, y1, (x0 - cx) / r, (y1 - cy) / r, color, aa));
        verts.add(new MeshElement.Vertex(x1, y1, (x1 - cx) / r, (y1 - cy) / r, color, aa));
        verts.add(new MeshElement.Vertex(x1, y0, (x1 - cx) / r, (y0 - cy) / r, color, aa));
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

        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, x, y, x + w, y + h);
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
        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, minX, minY, maxX, maxY);
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
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(atlas);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex(x, y, u0, v0, color));
        verts.add(new MeshElement.Vertex(x, y + h, u0, v1, color));
        verts.add(new MeshElement.Vertex(x + w, y + h, u1, v1, color));
        verts.add(new MeshElement.Vertex(x + w, y, u1, v0, color));

        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, x, y, x + w, y + h);
    }

    public static void roundedTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha) {
        roundedTexture(g, id, x, y, w, h, radius, alpha, 0f, 0f, 1f, 1f);
    }

    public static void roundedTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float radius, float alpha,
                                      float uOff, float vOff, float uScale, float vScale) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), linearSampler());

        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;

        float r = Math.min(radius, Math.min(w, h) / 2f);
        float x1 = x + w, y1 = y + h;
        List<MeshElement.Vertex> verts = new ArrayList<>();
        UV uv = new UV(x, y, w, h, uOff, vOff, uScale, vScale);

        if (r <= 0.5f) {
            texVertex(verts, x, y, uv, color);
            texVertex(verts, x, y1, uv, color);
            texVertex(verts, x1, y1, uv, color);
            texVertex(verts, x1, y, uv, color);
            submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, x, y, x1, y1);
            return;
        }

        texQuad(verts, x + r, y, x1 - r, y1, uv, color);
        texQuad(verts, x, y + r, x + r, y1 - r, uv, color);
        texQuad(verts, x1 - r, y + r, x1, y1 - r, uv, color);
        texCorner(verts, x + r, y + r, r, uv, color, 180, 270);
        texCorner(verts, x1 - r, y + r, r, uv, color, 270, 360);
        texCorner(verts, x1 - r, y1 - r, r, uv, color, 0, 90);
        texCorner(verts, x + r, y1 - r, r, uv, color, 90, 180);

        submit(g, RenderPipelines.GUI_TEXTURED, setup, verts, x, y, x1, y1);
    }

    private record UV(float ox, float oy, float w, float h, float uOff, float vOff, float uScale, float vScale) {
        float u(float px) {
            return uOff + ((px - ox) / w) * uScale;
        }

        float v(float py) {
            return vOff + ((py - oy) / h) * vScale;
        }
    }

    private static void texVertex(List<MeshElement.Vertex> verts, float px, float py, UV uv, int color) {
        verts.add(new MeshElement.Vertex(px, py, uv.u(px), uv.v(py), color));
    }

    private static void texQuad(List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1, UV uv, int color) {
        texVertex(verts, x0, y0, uv, color);
        texVertex(verts, x0, y1, uv, color);
        texVertex(verts, x1, y1, uv, color);
        texVertex(verts, x1, y0, uv, color);
    }

    private static void texCorner(List<MeshElement.Vertex> verts, float cx, float cy, float r, UV uv, int color, float startDeg, float endDeg) {
        int segments = Math.max(8, (int) (r * 2f));
        float step = (float) Math.toRadians((endDeg - startDeg) / segments);
        float start = (float) Math.toRadians(startDeg);
        for (int i = 0; i < segments; i++) {
            float a0 = start + step * i;
            float a1 = start + step * (i + 1);
            float p0x = cx + (float) Math.cos(a0) * r, p0y = cy + (float) Math.sin(a0) * r;
            float p1x = cx + (float) Math.cos(a1) * r, p1y = cy + (float) Math.sin(a1) * r;
            texVertex(verts, cx, cy, uv, color);
            texVertex(verts, p1x, p1y, uv, color);
            texVertex(verts, p0x, p0y, uv, color);
            texVertex(verts, p0x, p0y, uv, color);
        }
    }

    private static void quad(List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1, int color) {
        verts.add(new MeshElement.Vertex(x0, y0, 0, 0, color));
        verts.add(new MeshElement.Vertex(x0, y1, 0, 1, color));
        verts.add(new MeshElement.Vertex(x1, y1, 1, 1, color));
        verts.add(new MeshElement.Vertex(x1, y0, 1, 0, color));
    }

    private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup,
                               List<MeshElement.Vertex> verts, float x0, float y0, float x1, float y1) {
        submit(g, pipeline, setup, verts, false, x0, y0, x1, y1);
    }

    private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup setup,
                               List<MeshElement.Vertex> verts, boolean writeNormal, float x0, float y0, float x1, float y1) {
        state(g).addGuiElement(new MeshElement(pipeline, setup, pose(g), verts, writeNormal, x0, y0, x1, y1, scissor(g)));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
