package tritium.music.client.rendering.shader;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import tritium.music.client.render.RenderContext;

import java.util.ArrayList;
import java.util.List;

public final class EffectQueue {

    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static final List<Region> BLURS = new ArrayList<>();
    private static final List<Region> BLOOMS = new ArrayList<>();

    private EffectQueue() {
    }

    public static void beginFrame() {
        BLURS.clear();
        BLOOMS.clear();
        CAPTURE.remove();
    }

    public static void finishFrame() {
        BLURS.clear();
        BLOOMS.clear();
        CAPTURE.remove();
    }

    public static void captureBlur(List<Runnable> renderers) {
        capture(renderers, BLURS);
    }

    public static void captureBloom(List<Runnable> renderers) {
        capture(renderers, BLOOMS);
    }

    public static void captureBackdrop(Runnable renderer) {
        capture(List.of(renderer), BLURS);
    }

    private static void capture(List<Runnable> renderers, List<Region> destination) {
        Capture previous = CAPTURE.get();
        CAPTURE.set(new Capture(destination));
        try {
            renderers.forEach(Runnable::run);
        } finally {
            if (previous == null) {
                CAPTURE.remove();
            } else {
                CAPTURE.set(previous);
            }
        }
    }

    public static boolean captureRect(float x, float y, float width, float height, float radius, int color) {
        Capture capture = CAPTURE.get();
        if (capture == null) {
            return false;
        }

        Matrix3x2fc pose = RenderContext.graphics().pose();
        Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
        Vector2f p1 = pose.transformPosition(x + width, y, new Vector2f());
        Vector2f p2 = pose.transformPosition(x + width, y + height, new Vector2f());
        Vector2f p3 = pose.transformPosition(x, y + height, new Vector2f());
        float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        float sx = (float) Math.hypot(pose.m00(), pose.m01());
        float sy = (float) Math.hypot(pose.m10(), pose.m11());
        float transformedRadius = radius * Math.min(sx, sy);
        float alpha = ((color >>> 24) & 255) / 255f;
        capture.destination.add(new Region(minX, minY, maxX - minX, maxY - minY, transformedRadius, alpha));
        return true;
    }

    public static List<Region> blurs() {
        return List.copyOf(BLURS);
    }

    public static List<Region> blooms() {
        return List.copyOf(BLOOMS);
    }

    private record Capture(List<Region> destination) {
    }

    public record Region(float x, float y, float width, float height, float radius, float alpha) {
    }
}
