package tritium.music.client.render;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public record ClipRect(float left, float top, float right, float bottom) {

    public static final ClipRect UNBOUNDED = new ClipRect(-1.0e6f, -1.0e6f, 1.0e6f, 1.0e6f);

    public static ClipRect of(Matrix3x2fc pose, double x, double y, double width, double height) {
        Vector2f p0 = pose.transformPosition((float) x, (float) y, new Vector2f());
        Vector2f p1 = pose.transformPosition((float) (x + width), (float) y, new Vector2f());
        Vector2f p2 = pose.transformPosition((float) (x + width), (float) (y + height), new Vector2f());
        Vector2f p3 = pose.transformPosition((float) x, (float) (y + height), new Vector2f());
        return new ClipRect(
                Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)),
                Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)),
                Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)),
                Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y))
        );
    }

    public ClipRect intersection(ClipRect other) {
        return new ClipRect(
                Math.max(left, other.left),
                Math.max(top, other.top),
                Math.min(right, other.right),
                Math.min(bottom, other.bottom)
        );
    }
}
