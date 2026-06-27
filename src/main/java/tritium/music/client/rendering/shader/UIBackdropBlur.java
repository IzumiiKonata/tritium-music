package tritium.music.client.rendering.shader;

/**
 * Frosted-glass backdrop blur for widgets. The Blaze3D implementation is not yet
 * wired up; the mask render runs directly so the widget still draws without blur.
 */
public class UIBackdropBlur {

    public void draw(double x, double y, double width, double height, Runnable maskRenderer) {
        maskRenderer.run();
    }
}
