package tritium.music.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

public record ClipRect(float left, float top, float right, float bottom) {

    public static final ClipRect UNBOUNDED = new ClipRect(-1.0e6f, -1.0e6f, 1.0e6f, 1.0e6f);

    public static ClipRect of(PoseStack poseStack, double x, double y, double width, double height) {
        Matrix4f matrix = poseStack.last().pose();
        float[] xs = new float[4];
        float[] ys = new float[4];
        corner(matrix, (float) x, (float) y, xs, ys, 0);
        corner(matrix, (float) (x + width), (float) y, xs, ys, 1);
        corner(matrix, (float) (x + width), (float) (y + height), xs, ys, 2);
        corner(matrix, (float) x, (float) (y + height), xs, ys, 3);
        return new ClipRect(
                Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3])),
                Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3])),
                Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3])),
                Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]))
        );
    }

    private static void corner(Matrix4f matrix, float x, float y, float[] xs, float[] ys, int index) {
        xs[index] = matrix.m00() * x + matrix.m10() * y + matrix.m30();
        ys[index] = matrix.m01() * x + matrix.m11() * y + matrix.m31();
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
