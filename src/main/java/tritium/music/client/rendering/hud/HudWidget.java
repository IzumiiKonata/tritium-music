package tritium.music.client.rendering.hud;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.animation.Interpolations;

public abstract class HudWidget implements SharedRenderingConstants {

    /** Wraps a render callback in the per-frame RenderContext + frame-delta setup shared by the HUD and editor. */
    public static void renderInFrame(GuiGraphicsExtractor graphics, float partialTick, Runnable render) {
        RenderContext.begin(graphics, partialTick);
        Interpolations.calcFrameDelta();
        try {
            graphics.pose().pushMatrix();
            try {
                double normalizer = RenderSystem.getScaleNormalizer();
                graphics.pose().scale((float) normalizer, (float) normalizer);
                render.run();
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            RenderContext.end();
        }
    }

    @Getter
    private final String name;

    @Setter
    private double width = -1, height = -1;

    protected HudWidget(String name) {
        this.name = name;
    }

    /**
     * Per-widget config slice (position fraction, scale, enabled). Subclasses bind it
     * so the base can resolve absolute position and the editor can mutate it.
     */
    public abstract WidgetConfig.WidgetSettings settings();

    public boolean isEnabled() {
        return settings().enabled;
    }

    public void setEnabled(boolean enabled) {
        settings().enabled = enabled;
    }

    public double scaleFactor() {
        return settings().scale;
    }

    @Override
    public double getWidth() {
        return this.width * scaleFactor();
    }

    @Override
    public double getHeight() {
        return this.height * scaleFactor();
    }

    public double getX() {
        return settings().x * RenderSystem.getWidth();
    }

    public double getY() {
        return settings().y * RenderSystem.getHeight();
    }

    public void setX(double x) {
        settings().x = x / RenderSystem.getWidth();
    }

    public void setY(double y) {
        settings().y = y / RenderSystem.getHeight();
    }

    /** Last-rendered width/height in pixels, for editor hit-testing and outlines. */
    public double editorWidth() {
        return getWidth();
    }

    public double editorHeight() {
        return getHeight();
    }

    public abstract void onRender();
}
