package tritium.music.client.rendering.ui.widgets;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

public class SliderWidget extends AbstractWidget<SliderWidget> {

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> formatter;
    private final CFontRenderer font = FontManager.pf12bold;
    private boolean dragging;
    private float hoverAnimation;

    public SliderWidget(
            DoubleSupplier getter,
            DoubleConsumer setter,
            double min,
            double max,
            double step,
            DoubleFunction<String> formatter) {
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.setBounds(156, 18);
        this.setShouldOverrideMouseCursor(true);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (dragging) {
            if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                update(mouseX - getX());
            } else {
                dragging = false;
            }
        }

        boolean hovered = dragging || isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
        hoverAnimation = Interpolations.interpolate(hoverAnimation, hovered ? 1f : 0f, 0.3f);
        if (hoverAnimation > 0.004f) {
            roundedRect(getX(), getY(), getWidth(), getHeight(), 4,
                    reAlpha(0xFFFFFFFF, getAlpha() * hoverAnimation * 0.07f));
        }

        double labelWidth = 46;
        double trackX = getX() + 8;
        double trackWidth = getWidth() - labelWidth - 16;
        double trackHeight = 3 + hoverAnimation * 2;
        double trackY = getY() + (getHeight() - trackHeight) * 0.5;
        double progress = clamp((getter.getAsDouble() - min) / (max - min), 0, 1);
        roundedRect(trackX, trackY, trackWidth, trackHeight, 1,
                reAlpha(0xFFFFFFFF, getAlpha() * 0.2f));
        double filledWidth = trackWidth * progress;
        if (filledWidth > 0) {
            roundedRect(trackX, trackY, filledWidth, trackHeight, Math.min(1, filledWidth * 0.5),
                    reAlpha(0xFFFFFFFF, getAlpha()));
        }

//        double knob = 6 + hoverAnimation * 2;
//        double knobX = trackX + trackWidth * progress - knob * 0.5;
//        double knobY = getY() + (getHeight() - knob) * 0.5;
//        roundedRect(knobX, knobY, knob, knob, knob * 0.5,
//                reAlpha(0xFFC30218, getAlpha()));

        String label = formatter.apply(getter.getAsDouble());
        double textY = getY() + (getHeight() - font.getStringHeight(label)) * 0.5;
        font.drawCenteredString(label, getX() + getWidth() - labelWidth * 0.5 - 4, textY, reAlpha(0xFFF2F3F5, getAlpha()));
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        dragging = true;
        update(relativeX);
        return true;
    }

    private void update(double relativeX) {
        double labelWidth = 46;
        double trackX = 8;
        double trackWidth = getWidth() - labelWidth - 16;
        double normalized = clamp((relativeX - trackX) / trackWidth, 0, 1);
        double value = min + normalized * (max - min);
        setter.accept(clamp(Math.round(value / step) * step, min, max));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
