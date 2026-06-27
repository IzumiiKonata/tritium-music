package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;

import java.awt.Color;
import java.util.function.Supplier;

public class RoundedButtonWidget extends RoundedRectWidget {

    LabelWidget lw;

    private float hoverAnim = 0f;
    private float pressAnim = 0f;

    public RoundedButtonWidget(Supplier<String> label, CFontRenderer fr) {
        lw = new LabelWidget(label, fr);

        this.addChild(lw);
        lw.setClickable(false);

        lw.setBeforeRenderCallback(() -> lw.center());

        this.setShouldOverrideMouseCursor(true);

        this.setTransformations(() -> {
            if (pressAnim > 0.001f) {
                this.scaleAtPos(this.getX() + this.getWidth() * 0.5, this.getY() + this.getHeight() * 0.5, 1 - pressAnim * 0.06);
            }
        });
    }

    public RoundedButtonWidget(String label, CFontRenderer fr) {
        this(() -> label, fr);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        super.onRender(mouseX, mouseY);

        hoverAnim = Interpolations.interpolate(hoverAnim, this.isHovering() ? 1f : 0f, 0.25f);
        pressAnim = Interpolations.interpolate(pressAnim, 0f, 0.22f);

        if (hoverAnim > 0.004f) {
            this.roundedRect(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.getRadius(), hexColor(1f, 1f, 1f, 0.14f * hoverAnim * this.getAlpha()));
        }
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        this.pressAnim = 1f;
        return super.onMouseClicked(relativeX, relativeY, mouseButton);
    }

    public RoundedButtonWidget setTextColor(int color) {
        lw.setColor(color);
        return this;
    }

    public RoundedButtonWidget setTextColor(Color color) {
        lw.setColor(color);
        return this;
    }
}
