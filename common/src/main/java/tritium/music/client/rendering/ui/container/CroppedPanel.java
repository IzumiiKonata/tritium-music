package tritium.music.client.rendering.ui.container;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;

public class CroppedPanel extends Panel {

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        StencilClipManager.beginClip(() -> Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1));

        super.renderWidget(mouseX, mouseY, dWheel);

        StencilClipManager.endClip();
    }
}
