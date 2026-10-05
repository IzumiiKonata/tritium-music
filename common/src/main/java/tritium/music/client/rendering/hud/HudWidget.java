package tritium.music.client.rendering.hud;

import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.render.GuiStateReset;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.screens.WidgetEditorScreen;

public abstract class HudWidget implements SharedRenderingConstants {

    public static void renderInFrame(GuiGraphics graphics, float partialTick, Runnable render) {
        RenderContext.begin(graphics, partialTick);
        Interpolations.calcFrameDelta();
        tritium.music.client.render.Render.setHudOverlay(true);
        try {
            graphics.pose().pushPose();
            try {
                double normalizer = RenderSystem.getScaleNormalizer();
                double offsetX = RenderSystem.getOffsetX();
                double offsetY = RenderSystem.getOffsetY();
                graphics.pose().translate((float) offsetX, (float) offsetY, 0f);
                graphics.pose().scale((float) normalizer, (float) normalizer, 1f);
                render.run();
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            tritium.music.client.render.Render.setHudOverlay(false);
            tritium.music.client.rendering.font.TextureAtlas.flushAllDirty();
            tritium.music.client.rendering.StencilClipManager.endFrame();
            GuiStateReset.restore();
            RenderContext.end();
        }
    }

    private final String nameKey;

    @Setter
    private double width = -1, height = -1;

    @Setter
    private boolean preview;

    protected HudWidget(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getName() {
        return I18n.get(nameKey);
    }

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

    public boolean isPreview() {
        return preview;
    }

    public boolean editorOrPreview() {
        return preview || Minecraft.getInstance().screen instanceof WidgetEditorScreen;
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public double getHeight() {
        return this.height;
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

    public double editorWidth() {
        return this.width * scaleFactor();
    }

    public double editorHeight() {
        return this.height * scaleFactor();
    }

    public final void render() {
        double scale = scaleFactor();
        if (Math.abs(scale - 1) < 0.0001) {
            onRender();
            return;
        }
        var pose = RenderContext.graphics().pose();
        pose.pushPose();
        try {
            pose.translate(getX(), getY(), 0);
            pose.scale((float) scale, (float) scale, 1f);
            pose.translate(-getX(), -getY(), 0);
            onRender();
        } finally {
            pose.popPose();
        }
    }

    public abstract void onRender();
}

