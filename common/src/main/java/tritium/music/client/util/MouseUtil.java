package tritium.music.client.util;

import net.minecraft.client.Minecraft;

public final class MouseUtil {

    public static final int BUTTON_LEFT = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int BUTTON_RIGHT = 3;
    public static final int BUTTON_BACK = 4;
    public static final int BUTTON_FORWARD = 5;

    private static boolean leftDown;
    private static boolean middleDown;
    private static boolean rightDown;

    private MouseUtil() {
    }

    public static boolean isButtonDown(int button) {
        if (Minecraft.getInstance().gui.screen() != null) {
            return switch (button) {
                case BUTTON_LEFT -> leftDown;
                case BUTTON_MIDDLE -> middleDown;
                case BUTTON_RIGHT -> rightDown;
                default -> false;
            };
        }

        var mouseHandler = Minecraft.getInstance().mouseHandler;
        return switch (button) {
            case BUTTON_LEFT -> mouseHandler.isLeftPressed();
            case BUTTON_MIDDLE -> mouseHandler.isMiddlePressed();
            case BUTTON_RIGHT -> mouseHandler.isRightPressed();
            default -> false;
        };
    }

    public static void setButtonDown(int button, boolean down) {
        switch (button) {
            case BUTTON_LEFT -> leftDown = down;
            case BUTTON_MIDDLE -> middleDown = down;
            case BUTTON_RIGHT -> rightDown = down;
            default -> {
            }
        }
    }

    public static void clearButtons() {
        leftDown = false;
        middleDown = false;
        rightDown = false;
    }

    public static boolean isLeftDown() {
        return isButtonDown(BUTTON_LEFT);
    }

    public static boolean isRightDown() {
        return isButtonDown(BUTTON_RIGHT);
    }
}
