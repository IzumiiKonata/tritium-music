package tritium.music.client.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import tritium.music.client.render.ClipRect;
import tritium.music.client.render.RenderContext;

import java.util.ArrayDeque;
import java.util.Deque;

public class StencilClipManager {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("tritium-music/clip");

    private static final ThreadLocal<double[]> CAPTURE = new ThreadLocal<>();
    private static final Deque<ClipRect> stack = new ArrayDeque<>();
    private static final Deque<int[]> SUSPENDED = new ArrayDeque<>();

    public static void suspendScissor() {
        boolean enabled = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        SUSPENDED.push(new int[]{enabled ? 1 : 0, box[0], box[1], box[2], box[3]});
        GlStateManager._disableScissorTest();
    }

    public static void resumeScissor() {
        if (SUSPENDED.isEmpty()) {
            return;
        }
        int[] state = SUSPENDED.pop();
        GlStateManager._scissorBox(state[1], state[2], state[3], state[4]);
        if (state[0] != 0) {
            GlStateManager._enableScissorTest();
        }
    }

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
        ClipRect clip = ClipRect.of(RenderContext.graphics().pose(), x, y, width, height);
        ClipRect parent = stack.peek();
        ClipRect resolved = parent == null ? clip : parent.intersection(clip);
        stack.push(resolved);
        apply(resolved);
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
        if (stack.isEmpty()) {
            return;
        }
        stack.pop();
        if (RenderContext.active()) {
            RenderContext.graphics().disableScissor();
        } else {
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
    }

    public static void endFrame() {
        int leakedClips = stack.size();
        boolean leakedScissor = !SUSPENDED.isEmpty();
        if (leakedClips > 0) {
            clear();
        }
        SUSPENDED.clear();
        boolean scissorOn = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        if (scissorOn) {
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
        if (leakedClips > 0 || leakedScissor || scissorOn) {
            LOGGER.warn("repaired leaked clip state: clips={} suspended={} scissor={}", leakedClips, leakedScissor, scissorOn);
        }
    }

    public static void disable() {
        clear();
    }

    public static void clear() {
        if (!stack.isEmpty() && RenderContext.active()) {
            for (int i = 0; i < stack.size(); i++) {
                RenderContext.graphics().disableScissor();
            }
        }
        stack.clear();
    }

    private static void apply(ClipRect clip) {
        int x0 = floor(clip.left());
        int y0 = floor(clip.top());
        int x1 = ceil(clip.right());
        int y1 = ceil(clip.bottom());
        if (x1 < x0) {
            int swap = x0;
            x0 = x1;
            x1 = swap;
        }
        if (y1 < y0) {
            int swap = y0;
            y0 = y1;
            y1 = swap;
        }
        RenderContext.graphics().enableScissor(x0, y0, x1, y1);
    }

    private static int floor(float value) {
        return Float.isFinite(value) ? (int) Math.floor(value) : -1_000_000;
    }

    private static int ceil(float value) {
        return Float.isFinite(value) ? (int) Math.ceil(value) : 1_000_000;
    }
}
