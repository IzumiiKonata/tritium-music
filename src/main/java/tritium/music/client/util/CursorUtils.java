package tritium.music.client.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CursorUtils {

    public static final long ARROW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
    public static final long HAND = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
    public static final long TEXT = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);

    private static long overrideCursor = ARROW;
    private static long appliedCursor = -1;

    private CursorUtils() {
    }

    public static void resetOverride() {
        overrideCursor = ARROW;
    }

    public static void setOverride(long cursor) {
        overrideCursor = cursor;
    }

    public static void applyOverride() {
        if (overrideCursor != appliedCursor) {
            appliedCursor = overrideCursor;
            long handle = Minecraft.getInstance().getWindow().handle();
            GLFW.glfwSetCursor(handle, overrideCursor);
        }
    }
}
