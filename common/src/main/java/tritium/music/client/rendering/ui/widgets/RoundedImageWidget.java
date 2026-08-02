package tritium.music.client.rendering.ui.widgets;

import lombok.Getter;
import lombok.Setter;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.fabric.ui.Identifiers;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.util.function.Supplier;

public class RoundedImageWidget extends AbstractWidget<RoundedImageWidget> {

    @Getter
    @Setter
    private Supplier<TextureHandle> locImg;

    @Getter
    private double radius = 0;

    boolean fadeIn = false;

    @Getter
    boolean linearFilter = false;

    public RoundedImageWidget(Supplier<TextureHandle> locImg, double x, double y, double width, double height) {
        this.setBounds(x, y, width, height);
        this.locImg = locImg;
    }

    public RoundedImageWidget(TextureHandle locImg, double x, double y, double width, double height) {
        this(() -> locImg, x, y, width, height);
    }

    public RoundedImageWidget(double x, double y, double width, double height) {
        this(() -> null, x, y, width, height);
    }

    public RoundedImageWidget fadeIn() {
        fadeIn = true;
        this.setAlpha(0);
        return this;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        TextureHandle img = locImg.get();

        if (img == null || !Platform.hasTexture(img))
            return;

        if (fadeIn)
            this.setAlpha(Interpolations.interpolate(this.getWidgetAlpha(), 1.0f, 0.15f));

        RenderSystem.bindTexture(Identifiers.of(img));
        this.roundedRectTextured(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.getRadius(), this.getAlpha());
    }

    public RoundedImageWidget setRadius(double radius) {
        this.radius = radius;
        return this;
    }

    public RoundedImageWidget setLinearFilter(boolean linearFilter) {
        this.linearFilter = linearFilter;
        return this;
    }
}
