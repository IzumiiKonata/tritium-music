package tritium.music.client.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MouseUtil {

    private MouseUtil() {
    }

    public static boolean isButtonDown(int button) {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

    public static boolean isLeftDown() {
        return isButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public static boolean isRightDown() {
        return isButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }
}
