package tritium.music.client.rendering;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fc;
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
        Matrix3x2fc pose = RenderContext.graphics().pose();
        double scaleX = Math.sqrt((double) pose.m00() * pose.m00() + (double) pose.m01() * pose.m01());
        double scaleY = Math.sqrt((double) pose.m10() * pose.m10() + (double) pose.m11() * pose.m11());
        double slackX = 1.0 / Math.max(scaleX, 1e-6);
        double slackY = 1.0 / Math.max(scaleY, 1e-6);

        int x0 = (int) Math.floor(x - slackX);
        int y0 = (int) Math.floor(y - slackY);
        int x1 = (int) Math.ceil(x + width + slackX);
        int y1 = (int) Math.ceil(y + height + slackY);

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
            RenderContext.graphics().disableScissor();
        }
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
