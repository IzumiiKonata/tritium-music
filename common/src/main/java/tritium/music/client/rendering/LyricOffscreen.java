package tritium.music.client.rendering;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import tritium.music.client.rendering.font.Glyph;
import tritium.music.client.rendering.font.TextureAtlas;

import java.util.*;

public final class LyricOffscreen {

    private LyricOffscreen() {
    }

    public static void initialize() {
        LyricOffscreenPipelines.initialize();
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

        if (quads.isEmpty()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(rt.colorTexture(), 0);
            return;
        }

        int vertexCount = quads.size() * 4;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (MaskQuad quad : quads) {
                int leftColor = alphaColor(quad.leftAlpha());
                int rightColor = alphaColor(quad.rightAlpha());
                float left = clipX(quad.left(), w);
                float right = clipX(quad.right(), w);
                builder.addVertex(left, -1f, 0f).setColor(leftColor);
                builder.addVertex(left, 1f, 0f).setColor(leftColor);
                builder.addVertex(right, 1f, 0f).setColor(rightColor);
                builder.addVertex(right, -1f, 0f).setColor(rightColor);
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                GpuBuffer vertices = rt.uploadVertices(mesh.vertexBuffer());
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Tritium lyric stencil",
                        rt.colorView(), OptionalInt.of(0))) {
                    pass.setPipeline(LyricOffscreenPipelines.MASK);
                    pass.setVertexBuffer(0, vertices);
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
                }
            }
        }
    }

    public static void renderBaseGlyphs(TRenderTarget rt, int w, int h,
                                        Glyph[] glyphTable, int baseColor,
                                        List<GlyphCmd> glyphs, int blitScale) {
        TextureAtlas.flushAllDirty();
        Map<Identifier, List<GlyphQuad>> batches = new LinkedHashMap<>();
        int quadCount = 0;

        for (GlyphCmd cmd : glyphs) {
            Glyph glyph = glyphTable[cmd.ch];
            if (glyph == null || !glyph.uploaded || glyph.atlasIdentifier == null) {
                continue;
            }
            batches.computeIfAbsent(glyph.atlasIdentifier, ignored -> new ArrayList<>())
                    .add(new GlyphQuad(glyph, cmd.x, cmd.y));
            quadCount++;
        }

        if (quadCount == 0) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(rt.colorTexture(), 0);
            return;
        }

        int color = (((baseColor >>> 24) & 0xFF) << 24) | 0x00FFFFFF;
        List<GlyphBatch> draws = new ArrayList<>(batches.size());
        int vertexCount = quadCount * 4;

        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int firstQuad = 0;

            for (Map.Entry<Identifier, List<GlyphQuad>> entry : batches.entrySet()) {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(entry.getKey());
                float atlasWidth = texture.getTexture().getWidth(0);
                float atlasHeight = texture.getTexture().getHeight(0);
                float du = 1f / (blitScale * atlasWidth);
                float dv = 1f / (blitScale * atlasHeight);

                for (GlyphQuad quad : entry.getValue()) {
                    Glyph glyph = quad.glyph();
                    float left = quad.x() - 1f;
                    float top = quad.y() - 1f;
                    float right = quad.x() + glyph.width * blitScale + 1f;
                    float bottom = quad.y() + glyph.height * blitScale + 1f;
                    float u0 = glyph.u0 - du;
                    float v0 = glyph.v0 - dv;
                    float u1 = glyph.u1 + du;
                    float v1 = glyph.v1 + dv;

                    builder.addVertex(clipX(left, w), clipY(top, h), 0f).setUv(u0, v0).setColor(color);
                    builder.addVertex(clipX(left, w), clipY(bottom, h), 0f).setUv(u0, v1).setColor(color);
                    builder.addVertex(clipX(right, w), clipY(bottom, h), 0f).setUv(u1, v1).setColor(color);
                    builder.addVertex(clipX(right, w), clipY(top, h), 0f).setUv(u1, v0).setColor(color);
                }

                draws.add(new GlyphBatch(texture, firstQuad, entry.getValue().size()));
                firstQuad += entry.getValue().size();
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                GpuBuffer vertices = rt.uploadVertices(mesh.vertexBuffer());
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Tritium lyric glyphs",
                        rt.colorView(), OptionalInt.of(0))) {
                    pass.setPipeline(LyricOffscreenPipelines.GLYPH);
                    pass.setVertexBuffer(0, vertices);
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    for (GlyphBatch draw : draws) {
                        pass.bindTexture("Sampler0", draw.texture().getTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
                        pass.drawIndexed(0, draw.firstQuad() * 6, draw.quadCount() * 6, 1);
                    }
                }
            }
        }
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
        return (Math.clamp(Math.round(alpha * 255f), 0, 255) << 24) | 0x00FFFFFF;
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

    private record GlyphBatch(AbstractTexture texture, int firstQuad, int quadCount) {
    }

    public record GlyphCmd(char ch, float x, float y) {
    }
}
