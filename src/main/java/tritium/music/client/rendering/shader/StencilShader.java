package tritium.music.client.rendering.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3x2f;
import tritium.music.client.render.MeshElement;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.LuminRenderTarget;
import tritium.music.client.rendering.StencilCompositePipeline;

import java.util.ArrayList;
import java.util.List;

public class StencilShader {

    public void draw(LuminRenderTarget base, LuminRenderTarget stencil,
                     double x, double y, double width, double height,
                     double uMax, double vMax) {
        draw(base, stencil, x, y, width, height, uMax, vMax, 1.0f);
    }

    public void draw(LuminRenderTarget base, LuminRenderTarget stencil,
                     double x, double y, double width, double height,
                     double uMax, double vMax, float alpha) {
        if (alpha <= 0.004f) return;

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        TextureSetup textureSetup = TextureSetup.doubleTexture(
                base.colorView(), sampler,
                stencil.colorView(), sampler
        );

        float x1 = (float) (x + width);
        float y1 = (float) (y + height);
        float u1 = (float) uMax;
        float v1 = (float) vMax;
        int quadColor = (Math.round(alpha * 255f) << 24) | 0x00FFFFFF;

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex((float) x, (float) y, 0f, 0f, quadColor));
        verts.add(new MeshElement.Vertex((float) x, y1, 0f, v1, quadColor));
        verts.add(new MeshElement.Vertex(x1, y1, u1, v1, quadColor));
        verts.add(new MeshElement.Vertex(x1, (float) y, u1, 0f, quadColor));

        var g = RenderContext.graphics();
        RenderContext.graphics().guiRenderState.addGuiElement(new MeshElement(
                StencilCompositePipeline.PIPELINE,
                textureSetup,
                new Matrix3x2f(g.pose()),
                verts,
                true,
                false,
                (float) x, (float) y, x1, y1,
                g.scissorStack.peek()
        ));
    }
}
