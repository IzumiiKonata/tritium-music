package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleWidget extends AbstractWidget<ToggleWidget> {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float animation;

    public ToggleWidget(BooleanSupplier getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
        this.setBounds(32, 16);
        this.setShouldOverrideMouseCursor(true);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        animation = Interpolations.interpolate(animation, getter.getAsBoolean() ? 1f : 0f, 0.35f);
        int background = getter.getAsBoolean() ? 0xFFC30218 : 0xFF35373D;
        double inset = 3;
        double knob = getHeight() - inset * 2;
        double knobX = getX() + inset + (getWidth() - knob - inset * 2) * animation;
        roundedRect(getX(), getY(), getWidth(), getHeight(), 7, reAlpha(background, getAlpha()));
        roundedRect(knobX, getY() + inset, knob, knob, 4, reAlpha(0xFFFFFFFF, getAlpha()));
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        setter.accept(!getter.getAsBoolean());
        return true;
    }
}
