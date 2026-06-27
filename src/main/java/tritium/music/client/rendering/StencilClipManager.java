package tritium.music.client.rendering;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import tritium.music.client.render.RenderContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * In the original client this used GL stencil buffers; every clip region the
 * music UI uses is rectangular, so on the deferred Blaze3D pipeline it is
 * implemented with the scissor stack instead.
 */
public class StencilClipManager {

    private static final ThreadLocal<double[]> CAPTURE = new ThreadLocal<>();
    private static final Deque<ScreenRectangle> stack = new ArrayDeque<>();

    public static boolean stencilClipping() {
        return !stack.isEmpty();
    }

    public static boolean capturing() {
        return CAPTURE.get() != null;
    }

    public static void captureRect(double x, double y, double width, double height) {
        double[] capture = CAPTURE.get();
        if (capture != null) {
            capture[0] = x;
            capture[1] = y;
            capture[2] = width;
            capture[3] = height;
        }
    }

    public static void beginClip(double x, double y, double width, double height) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = (int) Math.ceil(x + width);
        int y1 = (int) Math.ceil(y + height);

        stack.push(new ScreenRectangle(x0, y0, x1 - x0, y1 - y0));
        RenderContext.graphics().enableScissor(x0, y0, x1, y1);
    }

    public static void beginClip(Runnable drawClipShape) {
        double[] capture = new double[4];
        CAPTURE.set(capture);
        try {
            drawClipShape.run();
        } finally {
            CAPTURE.remove();
        }
        beginClip(capture[0], capture[1], capture[2], capture[3]);
    }

    public static void endClip() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
        RenderContext.graphics().disableScissor();
    }

    public static void disable() {
        clear();
    }

    public static void clear() {
        while (!stack.isEmpty()) {
            stack.pop();
            RenderContext.graphics().disableScissor();
        }
    }
}
