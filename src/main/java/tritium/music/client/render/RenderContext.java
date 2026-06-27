package tritium.music.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

public final class RenderContext {

    private static @Nullable GuiGraphicsExtractor current;
    private static float partialTick;

    private RenderContext() {
    }

    public static void begin(GuiGraphicsExtractor graphics, float partialTick) {
        RenderContext.current = graphics;
        RenderContext.partialTick = partialTick;
    }

    public static void end() {
        RenderContext.current = null;
    }

    public static GuiGraphicsExtractor graphics() {
        if (current == null) {
            throw new IllegalStateException("No active GuiGraphicsExtractor; rendering outside a frame");
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
