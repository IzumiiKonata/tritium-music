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
                     double x, double y, double width, double height) {
        draw(base, stencil, x, y, width, height, 1.0, 1.0);
    }

    public void draw(LuminRenderTarget base, LuminRenderTarget stencil,
                     double x, double y, double width, double height,
                     double uMax, double vMax) {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        TextureSetup textureSetup = TextureSetup.doubleTexture(
                base.colorView(), sampler,
                stencil.colorView(), sampler
        );

        float x1 = (float) (x + width);
        float y1 = (float) (y + height);
        float u1 = (float) uMax;
        float v1 = (float) vMax;
        int white = 0xFFFFFFFF;

        List<MeshElement.Vertex> verts = new ArrayList<>(4);
        verts.add(new MeshElement.Vertex((float) x, (float) y, 0f, 0f, white));
        verts.add(new MeshElement.Vertex((float) x, y1, 0f, v1, white));
        verts.add(new MeshElement.Vertex(x1, y1, u1, v1, white));
        verts.add(new MeshElement.Vertex(x1, (float) y, u1, 0f, white));

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
