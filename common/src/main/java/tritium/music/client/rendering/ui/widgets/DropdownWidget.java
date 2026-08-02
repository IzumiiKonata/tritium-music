package tritium.music.client.rendering.ui.widgets;

import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DropdownWidget<T> extends AbstractWidget<DropdownWidget<T>> {

    private static final double WIDTH = 128;
    private static final double ITEM_HEIGHT = 22;

    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final T[] values;
    private final Function<T, String> formatter;
    private final CFontRenderer font = FontManager.pf12bold;
    private boolean expanded;

    public DropdownWidget(Supplier<T> getter, Consumer<T> setter, T[] values, Function<T, String> formatter) {
        this.getter = getter;
        this.setter = setter;
        this.values = values;
        this.formatter = formatter;
        setBounds(WIDTH, ITEM_HEIGHT);
        setShouldOverrideMouseCursor(true);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        boolean mainHovered = isHovered(mouseX, mouseY, getX(), getY(), getWidth(), ITEM_HEIGHT);
        roundedRect(
                getX(),
                getY(),
                getWidth(),
                ITEM_HEIGHT,
                5,
                reAlpha(mainHovered || expanded ? 0xFF34363C : 0xFF2A2C31, getAlpha()));

        String current = formatter.apply(getter.get());
        drawText(current, getX() + 10, getY(), 0xFFF2F3F5);

        String arrow = expanded ? "▴" : "▾";
        double arrowY = getY() + (ITEM_HEIGHT - font.getStringHeight(arrow)) * 0.5;
        font.drawCenteredString(
                arrow,
                getX() + getWidth() - 12,
                arrowY,
                reAlpha(0xFFB8BBC2, getAlpha()));

        if (!expanded) {
            return;
        }

        double listY = getY() + ITEM_HEIGHT + 2;
        roundedRect(
                getX(),
                listY,
                getWidth(),
                values.length * ITEM_HEIGHT,
                5,
                reAlpha(0xFF24262B, getAlpha()));

        T selected = getter.get();
        for (int index = 0; index < values.length; index++) {
            double itemY = listY + index * ITEM_HEIGHT;
            boolean hovered = isHovered(mouseX, mouseY, getX(), itemY, getWidth(), ITEM_HEIGHT);
            if (hovered || values[index].equals(selected)) {
                Rect.draw(
                        getX() + 3,
                        itemY + 2,
                        getWidth() - 6,
                        ITEM_HEIGHT - 4,
                        reAlpha(hovered ? 0xFF393B42 : 0xFF303239, getAlpha()));
            }
            drawText(
                    formatter.apply(values[index]),
                    getX() + 10,
                    itemY,
                    values[index].equals(selected) ? 0xFFFFFFFF : 0xFFD2D4D9);
        }
    }

    private void drawText(String text, double x, double itemY, int color) {
        double textY = itemY + (ITEM_HEIGHT - font.getStringHeight(text)) * 0.5;
        font.drawString(text, x, textY, reAlpha(color, getAlpha()));
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (relativeY <= ITEM_HEIGHT) {
            expanded = !expanded;
            updateHeight();
            return true;
        }
        if (!expanded || relativeY < ITEM_HEIGHT + 2) {
            return false;
        }

        int index = (int) ((relativeY - ITEM_HEIGHT - 2) / ITEM_HEIGHT);
        if (index < 0 || index >= values.length) {
            return false;
        }
        setter.accept(values[index]);
        expanded = false;
        updateHeight();
        return true;
    }

    private void updateHeight() {
        setHeight(expanded ? ITEM_HEIGHT + 2 + values.length * ITEM_HEIGHT : ITEM_HEIGHT);
        if (getParent() != null) {
            getParent().setHeight(Math.max(40, 14 + getHeight()));
        }
    }
}
