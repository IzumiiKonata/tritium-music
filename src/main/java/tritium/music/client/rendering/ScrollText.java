package tritium.music.client.rendering;

import lombok.Setter;
import tritium.music.client.rendering.animation.Animation;
import tritium.music.client.rendering.animation.Easing;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.core.util.Timer;

import java.time.Duration;

public class ScrollText {

    public ScrollText() {
    }

    Timer t = new Timer();

    double scrollOffset = 0;

    String cachedText = "";

    @Setter
    long waitTime = 2500;

    @Setter
    boolean oneShot = false;

    public Animation anim = new Animation(Easing.LINEAR, Duration.ofMillis(0));

    public boolean isScrolling = false;

    public void reset() {
        t.reset();
        scrollOffset = 0;
        anim.reset();
        anim.setStartValue(0);
        anim.setValue(0);
        isScrolling = false;
    }

    public void render(CFontRenderer fr, String text, double x, double y, double width, int color) {

        if (!cachedText.equals(text)) {
            cachedText = text;
            this.reset();
        }

        double w = fr.getStringWidthD(text);

        if (w > width) {

            isScrolling = true;

            double exp = 2;

            StencilClipManager.beginClip(() -> Rect.draw(x, y - exp, width, fr.getHeight() + exp * 2, -1, Rect.RectType.EXPAND));

            fr.drawString(text, x + scrollOffset, y, color);

            double dest = -(w - width + 4);

            if (anim.getDuration() != 0) {
                scrollOffset = anim.run(dest);
            } else {
                String s = "    ";

                dest = -(w + fr.getStringWidth(s));

                boolean delayed = t.isDelayed(waitTime);
                if (delayed) {
                    scrollOffset = Interpolations.interpolateLinear((float) scrollOffset, (float) dest, 2f);
                }

                fr.drawString(s + text, x + w + scrollOffset, y, color);

                double delta = scrollOffset - dest;

                if (delta == 0) {
                    scrollOffset = 0;
                    t.reset();
                }
            }

            StencilClipManager.endClip();
        } else {
            isScrolling = false;
            scrollOffset = 0;
            fr.drawString(text, x, y, color);
        }
    }
}
