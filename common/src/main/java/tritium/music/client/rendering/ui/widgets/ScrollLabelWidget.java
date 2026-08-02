package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.core.util.Timer;

import java.util.function.Supplier;

public class ScrollLabelWidget extends LabelWidget {

    Timer t = new Timer();
    double scrollOffset = 0;

    long waitTime = 3000;

    @Override
    public void onRender(double mouseX, double mouseY) {

        double x = this.getX();
        double y = this.getY();
        double width = this.getWidth();
        String label = this.getLabel();

        StencilClipManager.beginClip(() -> {
            double exp = 2;
            Rect.draw(x, y - exp, width, font.getHeight() + exp * 2, -1);
        });

        font.drawString(label, x + this.scrollOffset, y, this.getHexColor());
        float stringWidth = font.getWidth(label);

        if (stringWidth > width) {

            String spacing = "    ";
            double dest = -(stringWidth + font.getWidth(spacing));

            if (t.isDelayed(waitTime)) {
                scrollOffset = Interpolations.interpolate(scrollOffset, dest, .5f);
            }

            font.drawString(spacing + label, x + stringWidth + scrollOffset, y, this.getHexColor());

            if (Math.abs(dest - scrollOffset) == 0) {
                scrollOffset = 0;
                t.reset();
            }

        } else {
            this.scrollOffset = 0d;
        }

        StencilClipManager.endClip();

        this.setHeight(font.getHeight());
    }

    public ScrollLabelWidget setWaitTime(long waitTime) {
        this.waitTime = waitTime;
        return this;
    }

    public ScrollLabelWidget setLabel(Supplier<String> label) {
        String lbl = this.getLabel();
        super.setLabel(label);

        if (!lbl.equals(label.get())) {
            this.scrollOffset = 0;
        }
        return this;
    }
}
