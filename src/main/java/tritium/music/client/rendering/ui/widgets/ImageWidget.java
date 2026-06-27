package tritium.music.client.rendering.ui.widgets;

import lombok.Getter;
import lombok.Setter;
import tritium.music.client.rendering.Image;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.util.function.Supplier;

public class ImageWidget extends AbstractWidget<ImageWidget> {

    @Getter
    @Setter
    private Supplier<TextureHandle> locImg;

    public ImageWidget(Supplier<TextureHandle> locImg, double x, double y, double width, double height) {
        this.setBounds(x, y, width, height);
        this.locImg = locImg;
    }

    public ImageWidget(TextureHandle locImg, double x, double y, double width, double height) {
        this(() -> locImg, x, y, width, height);
    }

    public ImageWidget(double x, double y, double width, double height) {
        this(() -> null, x, y, width, height);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        TextureHandle img = locImg.get();

        if (img == null || !Platform.hasTexture(img))
            return;

        Image.draw(img, this.getX(), this.getY(), this.getWidth(), this.getHeight(), Image.Type.NoColor);
    }
}
