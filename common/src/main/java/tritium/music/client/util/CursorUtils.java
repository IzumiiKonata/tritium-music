package tritium.music.client.util;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import tritium.music.client.render.RenderContext;

public final class CursorUtils {

    public static final CursorType ARROW = CursorTypes.ARROW;
    public static final CursorType HAND = CursorTypes.POINTING_HAND;
    public static final CursorType TEXT = CursorTypes.IBEAM;

    private static CursorType overrideCursor = ARROW;

    private CursorUtils() {
    }

    public static void resetOverride() {
        overrideCursor = ARROW;
    }

    public static void setOverride(CursorType cursor) {
        overrideCursor = cursor;
    }

    public static void applyOverride() {
        if (RenderContext.active()) {
            RenderContext.graphics().requestCursor(overrideCursor);
        } else {
            Minecraft.getInstance().getWindow().selectCursor(overrideCursor);
        }
    }
}
