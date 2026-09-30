package tritium.music.client.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class CursorUtils {

    public static final int ARROW = GLFW.GLFW_ARROW_CURSOR;
    public static final int HAND = GLFW.GLFW_POINTING_HAND_CURSOR;
    public static final int TEXT = GLFW.GLFW_IBEAM_CURSOR;

    private static final Map<Integer, Long> CURSORS = new HashMap<>();
    private static int overrideCursor = ARROW;

    private CursorUtils() {
    }

    public static void resetOverride() {
        overrideCursor = ARROW;
    }

    public static void setOverride(int cursor) {
        overrideCursor = cursor;
    }

    public static void applyOverride() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) {
            return;
        }
        GLFW.glfwSetCursor(window, CURSORS.computeIfAbsent(overrideCursor, GLFW::glfwCreateStandardCursor));
    }
}
