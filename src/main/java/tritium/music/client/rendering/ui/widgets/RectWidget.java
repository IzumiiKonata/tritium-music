package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.ui.AbstractWidget;

public class RectWidget extends AbstractWidget<RectWidget> {

    public RectWidget(double x, double y, double width, double height) {
        this.setBounds(x, y, width, height);
    }

    public RectWidget() {
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.getHexColor());
    }
}
