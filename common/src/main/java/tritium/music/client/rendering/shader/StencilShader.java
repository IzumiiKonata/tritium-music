package tritium.music.client.rendering.shader;

import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.TRenderTarget;

public class StencilShader {

    public void draw(TRenderTarget base, TRenderTarget stencil,
                     double x, double y, double width, double height,
                     double uMax, double vMax) {
        draw(base, stencil, x, y, width, height, uMax, vMax, 1.0f);
    }

    public void draw(TRenderTarget base, TRenderTarget stencil,
                     double x, double y, double width, double height,
                     double uMax, double vMax, float alpha) {
        if (alpha <= 0.004f || base == null || stencil == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        Render.stencilComposite(RenderContext.graphics(), base.colorTextureId(), stencil.colorTextureId(),
                (float) x, (float) y, (float) width, (float) height, (float) uMax, (float) vMax, alpha);
    }
}
