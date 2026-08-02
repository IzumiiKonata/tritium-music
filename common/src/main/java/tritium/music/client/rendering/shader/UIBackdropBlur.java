package tritium.music.client.rendering.shader;

public class UIBackdropBlur {

    public void draw(double x, double y, double width, double height, Runnable maskRenderer) {
        EffectQueue.captureBackdrop(maskRenderer);
    }
}
