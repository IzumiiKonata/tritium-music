package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

public class LoadingIndicatorWidget extends AbstractWidget<LoadingIndicatorWidget> {

    private static final int DOT_COUNT = 8;
    private static final double RADIUS = 14;
    private static final double DOT_SIZE = 5.6;
    private static final long CYCLE_MS = 900L;
    private static final double LABEL_GAP = 19;
    private static final float FADE_SPEED = 0.24f;
    private static final float FADE_EPSILON = 0.015f;

    private String label = "";
    private boolean loading;
    private float visibility;

    public LoadingIndicatorWidget() {
        this.setClickable(false);
    }

    public LoadingIndicatorWidget setLabel(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    public LoadingIndicatorWidget setLoading(boolean loading) {
        this.loading = loading;
        return this;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        this.visibility = Interpolations.interpolate(this.visibility, this.loading ? 1f : 0f, FADE_SPEED);

        if (this.visibility <= FADE_EPSILON)
            return;

        CFontRenderer font = FontManager.pf12;
        boolean hasLabel = !this.label.isEmpty() && font != null;

        double centerX = this.getX() + this.getWidth() * .5;
        double centerY = this.getY() + this.getHeight() * .5 - (hasLabel ? LABEL_GAP * .5 : 0);

        double phase = (System.currentTimeMillis() % CYCLE_MS) / (double) CYCLE_MS;

        for (int i = 0; i < DOT_COUNT; i++) {
            double progress = (i / (double) DOT_COUNT - phase + 1.0) % 1.0;
            double angle = i / (double) DOT_COUNT * Math.PI * 2 - Math.PI * .5;
            double size = DOT_SIZE * (.55 + .45 * progress);
            double dotVisibility = this.visibility * (.15 + .85 * progress);

            this.roundedRect(
                    centerX + Math.cos(angle) * RADIUS - size * .5,
                    centerY + Math.sin(angle) * RADIUS - size * .5,
                    size,
                    size,
                    size * .5 - .5,
                    this.reAlpha(this.getHexColor(), (float) dotVisibility));
        }

        if (hasLabel) {
            font.drawCenteredString(
                    this.label,
                    centerX,
                    centerY + RADIUS + LABEL_GAP * .5,
                    this.reAlpha(this.getHexColor(), this.getAlpha() * this.visibility * .7f));
        }
    }
}
