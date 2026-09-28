package tritium.music.client.rendering;

import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import tritium.music.client.render.ClipRect;
import tritium.music.client.render.RenderContext;

import java.util.ArrayDeque;
import java.util.Deque;

public class StencilClipManager {

    private static final ThreadLocal<double[]> CAPTURE = new ThreadLocal<>();
    private static final Deque<ClipRect> stack = new ArrayDeque<>();

    public static boolean stencilClipping() {
        return !stack.isEmpty();
    }

    public static boolean capturing() {
        return CAPTURE.get() != null;
    }

    public static @Nullable ClipRect currentClip() {
        return stack.peek();
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
        ClipRect clip = ClipRect.of(pose, x, y, width, height);
        ClipRect parent = stack.peek();
        stack.push(parent == null ? clip : parent.intersection(clip));
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
    }

    public static void disable() {
        clear();
    }

    public static void clear() {
        stack.clear();
    }
}
