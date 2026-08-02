package tritium.music.client.rendering.ui.container;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.ui.AbstractWidget;

public class Panel extends AbstractWidget<Panel> {
    @Override
    public void onRender(double mouseX, double mouseY) {
    }

    @Override
    protected void renderDebugLayout() {
        super.renderDebugLayout();
        Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), reAlpha(0x00F50EE8, this.getAlpha() * .05f));
    }
}
