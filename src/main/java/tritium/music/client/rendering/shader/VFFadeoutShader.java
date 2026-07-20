package tritium.music.client.rendering.shader;

import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;

public class VFFadeoutShader {

    public void draw(float x, float y, float width, float height, float controlPerc, float alpha) {
        if (width < 0) {
            x += width;
            width = -width;
        }
        if (height < 0) {
            y += height;
            height = -height;
        }
        if (RenderSystem.boundTexture() != null && width > 0 && height > 0 && alpha > 0) {
            Render.verticalFadeTexture(RenderContext.graphics(), RenderSystem.boundTexture(), x, y, width, height, controlPerc, alpha);
        }
    }

    public void draw(double x, double y, double width, double height, double controlPerc, float alpha) {
        draw((float) x, (float) y, (float) width, (float) height, (float) controlPerc, alpha);
    }
}
