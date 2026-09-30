package tritium.music.client.rendering.shader;

import org.joml.Matrix4f;
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
        captureBlur(renderers, 5f);
    }

    public static void captureBlur(List<Runnable> renderers, float blurRadius) {
        capture(renderers, BLURS, blurRadius);
    }

    public static void captureBloom(List<Runnable> renderers) {
        capture(renderers, BLOOMS, 0f);
    }

    public static void captureBackdrop(Runnable renderer) {
        captureBlur(List.of(renderer));
    }

    private static void capture(List<Runnable> renderers, List<Region> destination, float blurRadius) {
        Capture previous = CAPTURE.get();
        CAPTURE.set(new Capture(destination, blurRadius));
        boolean failed = false;
        try {
            renderers.forEach(Runnable::run);
        } catch (RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            if (previous == null) {
                CAPTURE.remove();
            } else {
                CAPTURE.set(previous);
            }
            if (failed) {
                destination.clear();
            }
        }
        if (destination.isEmpty()) {
            return;
        }
        try {
            PostEffectRenderer.render();
        } finally {
            destination.clear();
        }
    }
    public static boolean captureRect(float x, float y, float width, float height, float radius, int color) {
        Capture capture = CAPTURE.get();
        if (capture == null) {
            return false;
        }

        Matrix4f matrix = RenderContext.graphics().pose().last().pose();
        float p0x = transformX(matrix, x, y);
        float p0y = transformY(matrix, x, y);
        float p1x = transformX(matrix, x + width, y);
        float p1y = transformY(matrix, x + width, y);
        float p2x = transformX(matrix, x + width, y + height);
        float p2y = transformY(matrix, x + width, y + height);
        float p3x = transformX(matrix, x, y + height);
        float p3y = transformY(matrix, x, y + height);
        float minX = Math.min(Math.min(p0x, p1x), Math.min(p2x, p3x));
        float minY = Math.min(Math.min(p0y, p1y), Math.min(p2y, p3y));
        float maxX = Math.max(Math.max(p0x, p1x), Math.max(p2x, p3x));
        float maxY = Math.max(Math.max(p0y, p1y), Math.max(p2y, p3y));
        float scaleX = (float) Math.hypot(matrix.m00(), matrix.m01());
        float scaleY = (float) Math.hypot(matrix.m10(), matrix.m11());
        float transformedRadius = radius * Math.min(scaleX, scaleY);
        float alpha = ((color >>> 24) & 255) / 255f;
        capture.destination.add(new Region(minX, minY, maxX - minX, maxY - minY, transformedRadius, alpha, capture.blurRadius));
        return true;
    }

    private static float transformX(Matrix4f matrix, float x, float y) {
        return matrix.m00() * x + matrix.m10() * y + matrix.m30();
    }

    private static float transformY(Matrix4f matrix, float x, float y) {
        return matrix.m01() * x + matrix.m11() * y + matrix.m31();
    }

    public static List<Region> blurs() {
        return List.copyOf(BLURS);
    }

    public static List<Region> blooms() {
        return List.copyOf(BLOOMS);
    }

    private record Capture(List<Region> destination, float blurRadius) {
    }

    public record Region(float x, float y, float width, float height, float radius, float alpha, float blurRadius) {
    }
}
