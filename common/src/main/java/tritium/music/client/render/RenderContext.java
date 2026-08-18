package tritium.music.client.render;

import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

public final class RenderContext {

    private static @Nullable GuiGraphics current;
    private static float partialTick;

    private RenderContext() {
    }

    public static void begin(GuiGraphics graphics, float partialTick) {
        RenderContext.current = graphics;
        RenderContext.partialTick = partialTick;
    }

    public static void end() {
        RenderContext.current = null;
    }

    public static GuiGraphics graphics() {
        if (current == null) {
            throw new IllegalStateException("No active GuiGraphics; rendering outside a frame");
        }
        return current;
    }

    public static boolean active() {
        return current != null;
    }

    public static float partialTick() {
        return partialTick;
    }
}
