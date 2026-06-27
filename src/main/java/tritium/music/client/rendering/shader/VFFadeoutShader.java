package tritium.music.client.rendering.shader;

import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.RenderSystem;

/**
 * Vertical fade-out used by the coverflow reflection. The shader version is not
 * yet wired up; a simple top-to-bottom alpha gradient over the bound region is
 * used as an approximation.
 */
public class VFFadeoutShader {

    public void draw(float x, float y, float width, float height, float controlPerc, float alpha) {
        int top = RGBA.color(1f, 1f, 1f, alpha);
        int bottom = RGBA.color(1f, 1f, 1f, 0f);
        RenderSystem.drawGradientRectTopToBottom(x, y, x + width, y + height, top, bottom);
    }

    public void draw(double x, double y, double width, double height, double controlPerc, float alpha) {
        draw((float) x, (float) y, (float) width, (float) height, (float) controlPerc, alpha);
    }
}
