package tritium.music.client.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import tritium.music.client.render.TritiumShaders;
import tritium.music.client.rendering.font.Glyph;
import tritium.music.client.rendering.font.TextureAtlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LyricOffscreen {

    private static final String SHADER_MASK = "tritium_lyric_mask";
    private static final String SHADER_GLYPH = "tritium_lyric_glyph";

    private LyricOffscreen() {
    }

    public static void initialize() {
    }

    public static void renderStencilMask(TRenderTarget rt, int w, int h,
                                         double sungW, double gradW) {
        List<MaskQuad> quads = new ArrayList<>(2);
        float solidEnd = clamp((float) (sungW - gradW), 0f, w);
        float fadeEnd = clamp((float) sungW, 0f, w);

        if (solidEnd > 0f) {
            quads.add(new MaskQuad(0f, solidEnd, 1f, 1f));
        }
        if (fadeEnd > solidEnd) {
            quads.add(new MaskQuad(solidEnd, fadeEnd,
                    maskAlpha(solidEnd, sungW, gradW), maskAlpha(fadeEnd, sungW, gradW)));
        }

        ShaderInstance shader = TritiumShaders.get(SHADER_MASK, DefaultVertexFormat.POSITION_COLOR);
        if (shader == null) {
            return;
        }

        StencilClipManager.suspendScissor();
        try {
            rt.clear();
            if (quads.isEmpty()) {
                return;
            }
            prepareStates();
            RenderSystem.setShader(() -> shader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (MaskQuad quad : quads) {
                int leftColor = alphaColor(quad.leftAlpha());
                int rightColor = alphaColor(quad.rightAlpha());
                float left = clipX(quad.left(), w);
                float right = clipX(quad.right(), w);
                builder.vertex(left, -1f, 0f).color(leftColor).endVertex();
                builder.vertex(left, 1f, 0f).color(leftColor).endVertex();
                builder.vertex(right, 1f, 0f).color(rightColor).endVertex();
                builder.vertex(right, -1f, 0f).color(rightColor).endVertex();
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            restoreStates();
            StencilClipManager.resumeScissor();
            TRenderTarget.rebindMainTarget();
        }
    }

    public static void renderBaseGlyphs(TRenderTarget rt, int w, int h,
                                        int baseColor,
                                        List<GlyphCmd> glyphs, int blitScale) {
        TextureAtlas.flushAllDirty();
        Map<ResourceLocation, List<GlyphQuad>> batches = new LinkedHashMap<>();

        for (GlyphCmd cmd : glyphs) {
            Glyph glyph = cmd.glyph();
            if (glyph == null || !glyph.uploaded || glyph.atlasIdentifier == null) {
                continue;
            }
            batches.computeIfAbsent(glyph.atlasIdentifier, ignored -> new ArrayList<>())
                    .add(new GlyphQuad(glyph, cmd.x(), cmd.y()));
        }

        ShaderInstance shader = TritiumShaders.get(SHADER_GLYPH, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shader == null) {
            return;
        }

        StencilClipManager.suspendScissor();
        try {
            rt.clear();
            if (batches.isEmpty()) {
                return;
            }
            prepareStates();
            RenderSystem.setShader(() -> shader);
            int color = (((baseColor >>> 24) & 0xFF) << 24) | 0x00FFFFFF;

            for (Map.Entry<ResourceLocation, List<GlyphQuad>> entry : batches.entrySet()) {
                Glyph reference = entry.getValue().get(0).glyph();
                float atlasWidth = reference.atlasImage == null ? 0f : reference.atlasImage.getWidth();
                float atlasHeight = reference.atlasImage == null ? 0f : reference.atlasImage.getHeight();
                float du = atlasWidth <= 0f ? 0f : 0.5f / atlasWidth;
                float dv = atlasHeight <= 0f ? 0f : 0.5f / atlasHeight;
                float pad = 0.5f * blitScale;

                RenderSystem.setShaderTexture(0, entry.getKey());
                BufferBuilder builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

                for (GlyphQuad quad : entry.getValue()) {
                    Glyph glyph = quad.glyph();
                    float originX = glyph.originX * blitScale;
                    float originY = glyph.originY * blitScale;
                    float left = quad.x() + originX - pad;
                    float top = quad.y() + originY - pad;
                    float right = quad.x() + originX + glyph.bitmapWidth * blitScale + pad;
                    float bottom = quad.y() + originY + glyph.bitmapHeight * blitScale + pad;
                    float u0 = glyph.u0 - du;
                    float v0 = glyph.v0 - dv;
                    float u1 = glyph.u1 + du;
                    float v1 = glyph.v1 + dv;

                    builder.vertex(clipX(left, w), clipY(top, h), 0f).uv(u0, v0).color(color).endVertex();
                    builder.vertex(clipX(left, w), clipY(bottom, h), 0f).uv(u0, v1).color(color).endVertex();
                    builder.vertex(clipX(right, w), clipY(bottom, h), 0f).uv(u1, v1).color(color).endVertex();
                    builder.vertex(clipX(right, w), clipY(top, h), 0f).uv(u1, v0).color(color).endVertex();
                }

                BufferUploader.drawWithShader(builder.end());
            }
        } finally {
            restoreStates();
            StencilClipManager.resumeScissor();
            TRenderTarget.rebindMainTarget();
        }
    }

    private static void prepareStates() {
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlStateManager._colorMask(true, true, true, true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void restoreStates() {
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._enableCull();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static float maskAlpha(float x, double sungW, double gradW) {
        if (gradW <= 0) {
            return x < sungW ? 1f : 0f;
        }
        if (x < sungW - gradW) {
            return 1f;
        }
        if (x < sungW) {
            return clamp((float) ((sungW - x) / gradW), 0f, 1f);
        }
        return 0f;
    }

    private static int alphaColor(float alpha) {
        int value = Math.round(clamp(alpha, 0f, 1f) * 255f);
        return (value << 24) | 0x00FFFFFF;
    }

    private static float clipX(float x, int width) {
        return x * 2f / width - 1f;
    }

    private static float clipY(float y, int height) {
        return y * 2f / height - 1f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MaskQuad(float left, float right, float leftAlpha, float rightAlpha) {
    }

    private record GlyphQuad(Glyph glyph, float x, float y) {
    }

    public record GlyphCmd(Glyph glyph, float x, float y) {
    }
}
