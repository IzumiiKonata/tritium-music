package tritium.music.client.rendering.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import tritium.music.client.render.RenderContext;
import tritium.music.client.render.TritiumShaders;
import tritium.music.client.render.Uniforms;
import tritium.music.client.rendering.StencilClipManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PostEffectRenderer {

    private static final float BLUR_STEP_WIDTH = 0.5f;
    private static final int BLUR_COMPOSITE_PADDING = 7;
    private static final int BLOOM_COMPOSITE_PADDING = 50;
    private static final float BLOOM_RADIUS = 12f;
    private static final float BLOOM_STEP_WIDTH = 2f;
    private static final float INVISIBLE_ALPHA = 0.004f;

    private static final String SHADER_GAUSSIAN = "tritium_gaussian";
    private static final String SHADER_BLUR_COMPOSITE = "tritium_blur_composite";
    private static final String SHADER_BLOOM_MASK = "tritium_bloom_mask";
    private static final String SHADER_BLOOM_COMPOSITE = "tritium_bloom_composite";

    private static TextureTarget scratch;
    private static TextureTarget output;
    private static TextureTarget mask;
    private static int width = -1;
    private static int height = -1;

    private PostEffectRenderer() {
    }

    public static void render() {
        List<EffectQueue.Region> blurs = EffectQueue.blurs();
        List<EffectQueue.Region> blooms = EffectQueue.blooms();
        if (blurs.isEmpty() && blooms.isEmpty()) {
            return;
        }
        if (!RenderContext.active()) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        int guiScale = (int) minecraft.getWindow().getGuiScale();

        StencilClipManager.suspendScissor();
        try {
            ensureTargets(main.width, main.height);
            if (scratch == null || output == null || mask == null) {
                return;
            }
            main.bindWrite(true);
            RenderContext.graphics().flush();

            if (!blurs.isEmpty()) {
                Map<Float, List<EffectQueue.Region>> byRadius = new LinkedHashMap<>();
                for (EffectQueue.Region region : blurs) {
                    byRadius.computeIfAbsent(region.blurRadius(), ignored -> new ArrayList<>()).add(region);
                }
                for (Map.Entry<Float, List<EffectQueue.Region>> entry : byRadius.entrySet()) {
                    float radius = entry.getKey();
                    gaussian(main.getColorTextureId(), scratch, radius, BLUR_STEP_WIDTH, 1f, 0f);
                    gaussian(scratch.getColorTextureId(), output, radius, BLUR_STEP_WIDTH, 0f, 1f);
                    compositeBlur(main, output.getColorTextureId(), entry.getValue(), guiScale);
                }
            }

            for (EffectQueue.Region region : blooms) {
                if (region.alpha() <= INVISIBLE_ALPHA) {
                    continue;
                }
                renderBloom(main, region, guiScale);
            }
        } finally {
            main.bindWrite(true);
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._enableCull();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            StencilClipManager.resumeScissor();
        }
    }

    private static void ensureTargets(int targetWidth, int targetHeight) {
        if (scratch != null && width == targetWidth && height == targetHeight) {
            return;
        }
        destroy();
        scratch = createTarget(targetWidth, targetHeight);
        output = createTarget(targetWidth, targetHeight);
        mask = createTarget(targetWidth, targetHeight);
        width = targetWidth;
        height = targetHeight;
    }

    private static TextureTarget createTarget(int targetWidth, int targetHeight) {
        TextureTarget target = new TextureTarget(targetWidth, targetHeight, false, false);
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    private static void destroy() {
        if (scratch != null) {
            scratch.destroyBuffers();
        }
        if (output != null) {
            output.destroyBuffers();
        }
        if (mask != null) {
            mask.destroyBuffers();
        }
        scratch = null;
        output = null;
        mask = null;
    }

    private static void gaussian(int sourceTexture, TextureTarget destination, float radius, float stepWidth, float dx, float dy) {
        ShaderInstance shader = TritiumShaders.get(SHADER_GAUSSIAN, DefaultVertexFormat.POSITION);
        if (shader == null) {
            return;
        }
        Uniforms.set(shader, "BlurInfo", dx, dy, radius, stepWidth);
        pass(destination, shader, sourceTexture, false);
    }

    private static void compositeBlur(RenderTarget main, int blurredTexture, List<EffectQueue.Region> regions, int guiScale) {
        ShaderInstance shader = TritiumShaders.get(SHADER_BLUR_COMPOSITE, DefaultVertexFormat.POSITION);
        if (shader == null) {
            return;
        }
        for (EffectQueue.Region region : regions) {
            Uniforms.set(shader, "Opacity", region.alpha());
            setCompositeRect(shader, region, guiScale, main.width, main.height, BLUR_COMPOSITE_PADDING);
            pass(main, shader, blurredTexture, true);
        }
    }

    private static void renderBloom(RenderTarget main, EffectQueue.Region region, int guiScale) {
        ShaderInstance maskShader = TritiumShaders.get(SHADER_BLOOM_MASK, DefaultVertexFormat.POSITION);
        if (maskShader == null) {
            return;
        }
        clear(mask);
        setShapeInfo(maskShader, region, guiScale, main.height);
        Uniforms.set(maskShader, "ShapeOpacity", region.alpha());
        pass(mask, maskShader, 0, true);

        gaussian(mask.getColorTextureId(), scratch, BLOOM_RADIUS, BLOOM_STEP_WIDTH, 1f, 0f);
        gaussian(scratch.getColorTextureId(), output, BLOOM_RADIUS, BLOOM_STEP_WIDTH, 0f, 1f);

        ShaderInstance composite = TritiumShaders.get(SHADER_BLOOM_COMPOSITE, DefaultVertexFormat.POSITION);
        if (composite == null) {
            return;
        }
        setShapeInfo(composite, region, guiScale, main.height);
        setCompositeRect(composite, region, guiScale, main.width, main.height, BLOOM_COMPOSITE_PADDING);
        pass(main, composite, output.getColorTextureId(), true);
    }

    private static void clear(TextureTarget target) {
        target.bindWrite(true);
        GlStateManager._clearColor(0f, 0f, 0f, 0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, false);
    }

    private static void pass(RenderTarget destination, ShaderInstance shader, int sourceTexture, boolean blend) {
        destination.bindWrite(true);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlStateManager._colorMask(true, true, true, true);
        if (blend) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, sourceTexture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1f, -1f, 0f).endVertex();
        builder.vertex(-1f, 1f, 0f).endVertex();
        builder.vertex(1f, 1f, 0f).endVertex();
        builder.vertex(1f, -1f, 0f).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    private static void setShapeInfo(ShaderInstance shader, EffectQueue.Region region, int scale, int targetHeight) {
        float x = region.x() * scale;
        float y = targetHeight - (region.y() + region.height()) * scale;
        Uniforms.set(shader, "ShapeInfo", x, y, region.width() * scale, region.height() * scale);
        Uniforms.set(shader, "ShapeRadius", region.radius() * scale);
    }

    private static void setCompositeRect(ShaderInstance shader, EffectQueue.Region region, int scale,
                                         int targetWidth, int targetHeight, int padding) {
        float left = Math.max(0f, (float) Math.floor(region.x() * scale) - padding);
        float top = Math.max(0f, (float) Math.floor(region.y() * scale) - padding);
        float right = Math.min(targetWidth, (float) Math.ceil((region.x() + region.width()) * scale) + padding);
        float bottom = Math.min(targetHeight, (float) Math.ceil((region.y() + region.height()) * scale) + padding);
        Uniforms.set(shader, "CompositeRect", left, targetHeight - bottom, right, targetHeight - top);
    }
}
