package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.function.Supplier;

public class CycleButtonWidget extends AbstractWidget<CycleButtonWidget> {

    private final Supplier<String> label;
    private final Runnable cycle;
    private final CFontRenderer font = FontManager.pf12bold;

    public CycleButtonWidget(Supplier<String> label, Runnable cycle) {
        this.label = label;
        this.cycle = cycle;
        this.setBounds(128, 22);
        this.setShouldOverrideMouseCursor(true);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        roundedRect(getX(), getY(), getWidth(), getHeight(), 5, reAlpha(isHovering() ? 0xFF34363C : 0xFF2A2C31, getAlpha()));
        String text = label.get();
        double textY = getY() + (getHeight() - font.getStringHeight(text)) * 0.5;
        font.drawCenteredString(text, getX() + getWidth() * 0.5, textY, reAlpha(0xFFF2F3F5, getAlpha()));
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        cycle.run();
        return true;
    }
}
