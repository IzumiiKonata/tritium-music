package tritium.music.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.shader.EffectQueue;

import java.util.List;

public final class Render {

    public static final String SHADER_POSITION_COLOR = "tritium_position_color";
    public static final String SHADER_POSITION_TEX_COLOR = "tritium_position_tex_color";
    public static final String SHADER_ROUNDED = "tritium_rounded";
    public static final String SHADER_ROUNDED_GRADIENT = "tritium_rounded_gradient";
    public static final String SHADER_ROUNDED_OUTLINE = "tritium_rounded_outline";
    public static final String SHADER_ROUNDED_OUTLINE_GRADIENT = "tritium_rounded_outline_gradient";
    public static final String SHADER_ROUNDED_TEXTURE = "tritium_rounded_texture";
    public static final String SHADER_VERTICAL_FADE = "tritium_vertical_fade";
    public static final String SHADER_STENCIL_COMPOSITE = "tritium_stencil_composite";

    private Render() {
    }

    private static @Nullable ShaderInstance shader(String name, VertexFormat format) {
        return TritiumShaders.get(name, format);
    }

    private static Matrix4f matrix(GuiGraphics graphics) {
        return graphics.pose().last().pose();
    }

    private static boolean hudOverlay;

    public static void setHudOverlay(boolean value) {
        hudOverlay = value;
    }

    private static void begin(GuiGraphics graphics, boolean premultiplied) {
        graphics.flush();
        if (hudOverlay) {
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
        } else {
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL11.GL_LEQUAL);
            GlStateManager._depthMask(true);
        }
        GlStateManager._disableCull();
        GlStateManager._colorMask(true, true, true, true);
        RenderSystem.enableBlend();
        if (premultiplied) {
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private static void clip(ShaderInstance shader) {
        ClipRect clip = StencilClipManager.currentClip();
        if (clip == null) {
            clip = ClipRect.UNBOUNDED;
        }
        Uniforms.set(shader, "ClipRect", clip.left(), clip.top(), clip.right(), clip.bottom());
    }

    private static void upload(BufferBuilder builder, ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y, int color) {
        builder.vertex(matrix, x, y, 0f).color(color).endVertex();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float u, float v, int color) {
        builder.vertex(matrix, x, y, 0f).uv(u, v).color(color).endVertex();
    }

    public static void rect(GuiGraphics graphics, float x, float y, float w, float h, int color) {
        if (EffectQueue.captureRect(x, y, w, h, 0f, color)) {
            return;
        }
        ShaderInstance shader = shader(SHADER_POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        Dimensions dimensions = dimensions(x, y, w, h);
        begin(graphics, false);
        clip(shader);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), color);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), color);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), color);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), color);
        upload(builder, shader);
    }

    public static void lineStrip(GuiGraphics graphics, float[] points, int count,
                                 float offsetX, float offsetY, float yScale, int color) {
        if (count < 2) {
            return;
        }
        ShaderInstance shader = shader(SHADER_POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        begin(graphics, false);
        clip(shader);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float previousX = offsetX + points[0];
        float previousY = offsetY + points[1] * yScale;
        for (int i = 1; i < count; i++) {
            int index = i * 2;
            float x = offsetX + points[index];
            float y = offsetY + points[index + 1] * yScale;
            vertex(builder, matrix, previousX, previousY, color);
            vertex(builder, matrix, x, y, color);
            previousX = x;
            previousY = y;
        }
        upload(builder, shader);
    }

    public static void gradientV(GuiGraphics graphics, float x, float y, float w, float h, int top, int bottom) {
        ShaderInstance shader = shader(SHADER_POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        Dimensions dimensions = dimensions(x, y, w, h);
        begin(graphics, false);
        clip(shader);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), top);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), bottom);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), bottom);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), top);
        upload(builder, shader);
    }

    public static void gradientH(GuiGraphics graphics, float x, float y, float w, float h, int left, int right) {
        ShaderInstance shader = shader(SHADER_POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        Dimensions dimensions = dimensions(x, y, w, h);
        begin(graphics, false);
        clip(shader);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), left);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), left);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), right);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), right);
        upload(builder, shader);
    }

    public static void colorQuad(GuiGraphics graphics,
                                 float tlx, float tly, float blx, float bly, float brx, float bry, float trx, float trY,
                                 int color) {
        ShaderInstance shader = shader(SHADER_POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        begin(graphics, false);
        clip(shader);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, tlx, tly, color);
        vertex(builder, matrix, blx, bly, color);
        vertex(builder, matrix, brx, bry, color);
        vertex(builder, matrix, trx, trY, color);
        upload(builder, shader);
    }

    public static void roundedRect(GuiGraphics graphics, float x, float y, float w, float h, float radius, int color) {
        if (EffectQueue.captureRect(x, y, w, h, radius, color)) {
            return;
        }
        roundedSolid(graphics, SHADER_ROUNDED, x, y, w, h, radius, color, color, color, color);
    }

    public static void roundedGradient(GuiGraphics graphics, float x, float y, float w, float h, float radius,
                                       int bottomLeft, int topLeft, int bottomRight, int topRight) {
        roundedSolid(graphics, SHADER_ROUNDED_GRADIENT, x, y, w, h, radius, topLeft, bottomLeft, bottomRight, topRight);
    }

    public static void roundedOutline(GuiGraphics graphics, float x, float y, float w, float h, float radius,
                                      float thickness, int color) {
        roundedOutline(graphics, SHADER_ROUNDED_OUTLINE, x, y, w, h, radius, thickness, color, color, color, color);
    }

    public static void roundedOutlineGradient(GuiGraphics graphics, float x, float y, float w, float h, float radius,
                                              float thickness, int bottomLeft, int topLeft, int bottomRight, int topRight) {
        roundedOutline(graphics, SHADER_ROUNDED_OUTLINE_GRADIENT, x, y, w, h, radius, thickness,
                topLeft, bottomLeft, bottomRight, topRight);
    }

    public static void texture(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h, float alpha) {
        texture(graphics, id, x, y, w, h, 0f, 0f, 1f, 1f, alpha);
    }

    public static void texture(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h,
                               float u0, float v0, float u1, float v1, float alpha) {
        ShaderInstance shader = shader(SHADER_POSITION_TEX_COLOR, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
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
        int color = alphaColor(alpha);
        begin(graphics, false);
        clip(shader);
        RenderSystem.setShaderTexture(0, id);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), u0, v0, color);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), u0, v1, color);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), u1, v1, color);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), u1, v0, color);
        upload(builder, shader);
    }

    public static void texturedQuad(GuiGraphics graphics, ResourceLocation id,
                                    float tlx, float tly, float blx, float bly, float brx, float bry, float trx, float trY,
                                    boolean flipX, float alpha) {
        ShaderInstance shader = shader(SHADER_POSITION_TEX_COLOR, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
        float u0 = flipX ? 1f : 0f;
        float u1 = flipX ? 0f : 1f;
        int color = alphaColor(alpha);
        begin(graphics, false);
        clip(shader);
        RenderSystem.setShaderTexture(0, id);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, tlx, tly, u0, 0f, color);
        vertex(builder, matrix, blx, bly, u0, 1f, color);
        vertex(builder, matrix, brx, bry, u1, 1f, color);
        vertex(builder, matrix, trx, trY, u1, 0f, color);
        upload(builder, shader);
    }

    public static void verticalFadeTexture(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h,
                                           float controlPercent, float alpha) {
        ShaderInstance shader = shader(SHADER_VERTICAL_FADE, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
        Dimensions dimensions = dimensions(x, y, w, h);
        int color = alphaColor(alpha);
        begin(graphics, false);
        clip(shader);
        Uniforms.set(shader, "ControlPercent", controlPercent <= 0f ? 1f : controlPercent);
        RenderSystem.setShaderTexture(0, id);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), 0f, 0f, color);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), 0f, 1f, color);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), 1f, 1f, color);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), 1f, 0f, color);
        upload(builder, shader);
    }

    public static void roundedTexture(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h, float radius, float alpha) {
        roundedTexture(graphics, id, x, y, w, h, radius, alpha, 0f, 0f, 1f, 1f);
    }

    public static void roundedTexture(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h, float radius, float alpha,
                                      float u0, float v0, float u1, float v1) {
        roundedTextured(graphics, id, x, y, w, h, radius, alpha, u0, v0, u1, v1);
    }

    public static void roundedTextureSpecial(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h, float radius, float alpha,
                                             float uOffset, float vOffset, float uScale, float vScale) {
        roundedTextured(graphics, id, x, y, w, h, radius, alpha, uOffset, vOffset, uOffset + uScale, vOffset + vScale);
    }

    public static void glyph(GuiGraphics graphics, ResourceLocation atlas, float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1, int color) {
        glyph(graphics, atlas, x, y, w, h, u0, v0, u1, v1, color, color);
    }

    public static void glyph(GuiGraphics graphics, ResourceLocation atlas, float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1, int leftColor, int rightColor) {
        glyphs(graphics, atlas, List.of(new GlyphQuad(x, y, w, h, u0, v0, u1, v1, leftColor, rightColor)));
    }

    public static void glyphs(GuiGraphics graphics, ResourceLocation atlas, List<GlyphQuad> quads) {
        if (quads.isEmpty()) {
            return;
        }
        ShaderInstance shader = shader(SHADER_POSITION_TEX_COLOR, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
        begin(graphics, false);
        clip(shader);
        RenderSystem.setShaderTexture(0, atlas);
        Matrix4f matrix = matrix(graphics);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (GlyphQuad quad : quads) {
            float x1 = quad.x() + quad.width();
            float y1 = quad.y() + quad.height();
            vertex(builder, matrix, quad.x(), quad.y(), quad.u0(), quad.v0(), quad.leftColor());
            vertex(builder, matrix, quad.x(), y1, quad.u0(), quad.v1(), quad.leftColor());
            vertex(builder, matrix, x1, y1, quad.u1(), quad.v1(), quad.rightColor());
            vertex(builder, matrix, x1, quad.y(), quad.u1(), quad.v0(), quad.rightColor());
        }
        upload(builder, shader);
    }

    public static void stencilComposite(GuiGraphics graphics, int baseTexture, int stencilTexture,
                                        float x, float y, float w, float h, float uMax, float vMax, float alpha) {
        ShaderInstance shader = shader(SHADER_STENCIL_COMPOSITE, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
        int color = alphaColor(alpha);
        begin(graphics, true);
        clip(shader);
        RenderSystem.setShaderTexture(0, baseTexture);
        RenderSystem.setShaderTexture(1, stencilTexture);
        Matrix4f matrix = matrix(graphics);
        float x1 = x + w;
        float y1 = y + h;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, x, y, 0f, 0f, color);
        vertex(builder, matrix, x, y1, 0f, vMax, color);
        vertex(builder, matrix, x1, y1, uMax, vMax, color);
        vertex(builder, matrix, x1, y, uMax, 0f, color);
        upload(builder, shader);
    }

    public record GlyphQuad(float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1,
                            int leftColor, int rightColor) {
    }

    private static void roundedSolid(GuiGraphics graphics, String name, float x, float y, float w, float h,
                                     float radius, int topLeft, int bottomLeft, int bottomRight, int topRight) {
        Dimensions dimensions = dimensions(x, y, w, h);
        if (dimensions.width() <= 0f || dimensions.height() <= 0f) {
            return;
        }
        ShaderInstance shader = shader(name, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        Matrix4f matrix = matrix(graphics);
        begin(graphics, false);
        clip(shader);
        Uniforms.set(shader, "Radius", radius * poseScale(matrix));
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), topLeft);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), bottomLeft);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), bottomRight);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), topRight);
        upload(builder, shader);
    }

    private static void roundedOutline(GuiGraphics graphics, String name, float x, float y, float w, float h,
                                       float radius, float thickness, int topLeft, int bottomLeft, int bottomRight, int topRight) {
        Dimensions dimensions = dimensions(x, y, w, h);
        if (dimensions.width() <= 0f || dimensions.height() <= 0f || thickness <= 0f) {
            return;
        }
        ShaderInstance shader = shader(name, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }
        Matrix4f matrix = matrix(graphics);
        begin(graphics, false);
        clip(shader);
        float scale = poseScale(matrix);
        Uniforms.set(shader, "Radius", (radius - 2f) * scale);
        Uniforms.set(shader, "BorderSize", thickness * scale);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), topLeft);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), bottomLeft);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), bottomRight);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), topRight);
        upload(builder, shader);
    }

    private static void roundedTextured(GuiGraphics graphics, ResourceLocation id, float x, float y, float w, float h,
                                        float radius, float alpha, float u0, float v0, float u1, float v1) {
        Dimensions dimensions = dimensions(x, y, w, h);
        if (dimensions.width() <= 0f || dimensions.height() <= 0f) {
            return;
        }
        ShaderInstance shader = shader(SHADER_ROUNDED_TEXTURE, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }
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
        Matrix4f matrix = matrix(graphics);
        int color = alphaColor(alpha);
        begin(graphics, false);
        clip(shader);
        Uniforms.set(shader, "Radius", radius * poseScale(matrix));
        RenderSystem.setShaderTexture(0, id);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(builder, matrix, dimensions.x(), dimensions.y(), u0, v0, color);
        vertex(builder, matrix, dimensions.x(), dimensions.bottom(), u0, v1, color);
        vertex(builder, matrix, dimensions.right(), dimensions.bottom(), u1, v1, color);
        vertex(builder, matrix, dimensions.right(), dimensions.y(), u1, v0, color);
        upload(builder, shader);
    }

    private static float poseScale(Matrix4f matrix) {
        float scaleX = (float) Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01());
        float scaleY = (float) Math.sqrt(matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11());
        return Math.min(scaleX, scaleY);
    }

    private static int alphaColor(float alpha) {
        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        return (a << 24) | 0xFFFFFF;
    }

    private static Dimensions dimensions(float x, float y, float width, float height) {
        boolean flipX = width < 0f;
        boolean flipY = height < 0f;
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

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    private record Dimensions(float x, float y, float width, float height, boolean flipX, boolean flipY) {
        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }
}
