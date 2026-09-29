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
    private static final double SPINNER_OFFSET = -22;
    private static final double BAR_WIDTH = 190;
    private static final double BAR_HEIGHT = 5;
    private static final double BAR_OFFSET = 10;
    private static final double LABEL_OFFSET = 22;
    private static final float FADE_SPEED = 0.24f;
    private static final float FADE_EPSILON = 0.015f;

    private String label = "";
    private boolean loading;
    private double progress;
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

    public LoadingIndicatorWidget setProgress(double progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        return this;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        this.visibility = Interpolations.interpolate(this.visibility, this.loading ? 1f : 0f, FADE_SPEED);

        if (this.visibility <= FADE_EPSILON)
            return;

        CFontRenderer font = FontManager.pf12;

        double centerX = this.getX() + this.getWidth() * .5;
        double centerY = this.getY() + this.getHeight() * .5;

        this.renderSpinner(centerX, centerY + SPINNER_OFFSET);
        this.renderProgressBar(centerX, centerY + BAR_OFFSET);

        if (!this.label.isEmpty() && font != null) {
            font.drawCenteredString(
                    this.label,
                    centerX,
                    centerY + LABEL_OFFSET,
                    this.reAlpha(this.getHexColor(), this.getAlpha() * this.visibility * .7f));
        }
    }

    private void renderSpinner(double centerX, double centerY) {
        double phase = (System.currentTimeMillis() % CYCLE_MS) / (double) CYCLE_MS;

        for (int i = 0; i < DOT_COUNT; i++) {
            double tail = (i / (double) DOT_COUNT - phase + 1.0) % 1.0;
            double angle = i / (double) DOT_COUNT * Math.PI * 2 - Math.PI * .5;
            double size = DOT_SIZE * (.55 + .45 * tail);

            this.roundedRect(
                    centerX + Math.cos(angle) * RADIUS - size * .5,
                    centerY + Math.sin(angle) * RADIUS - size * .5,
                    size,
                    size,
                    size * .5 - .75,
                    this.reAlpha(this.getHexColor(), this.getAlpha() * (float) (this.visibility * (.15 + .85 * tail))));
        }
    }

    private void renderProgressBar(double centerX, double barY) {
        double barX = centerX - BAR_WIDTH * .5;
        double barRadius = BAR_HEIGHT * .5;

        this.roundedRect(barX, barY, BAR_WIDTH, BAR_HEIGHT, barRadius,
                this.reAlpha(this.getHexColor(), this.getAlpha() * this.visibility * .16f));

        double fillWidth = BAR_WIDTH * this.progress;

        if (fillWidth < .5)
            return;

        this.roundedRect(barX, barY, fillWidth, BAR_HEIGHT, barRadius,
                this.reAlpha(this.getHexColor(), this.getAlpha() * this.visibility * .9f));
    }
}
