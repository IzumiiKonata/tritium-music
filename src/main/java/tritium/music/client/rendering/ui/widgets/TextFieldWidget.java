package tritium.music.client.rendering.ui.widgets;

import lombok.Getter;
import tritium.music.client.rendering.TextField;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.util.CursorUtils;

public class TextFieldWidget extends AbstractWidget<TextFieldWidget> {

    @Getter
    private final TextField textField;

    public TextFieldWidget(CFontRenderer fontRenderer) {
        this.textField = new TextField();
        this.textField.setFontRenderer(fontRenderer);
        this.setShouldOverrideMouseCursor(true);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        this.textField.xPosition = (float) this.getX();
        this.textField.yPosition = (float) this.getY();
        this.textField.width = (float) this.getWidth();
        this.textField.height = (float) this.getHeight();
        this.textField.enabledColor = this.getHexColor();
        this.textField.setWholeAlpha(this.getAlpha());
        this.textField.drawTextBox((int) mouseX, (int) mouseY);
    }

    @Override
    public long getHoveringCursorType() {
        return CursorUtils.TEXT;
    }

    @Override
    public boolean onKeyTyped(char character, int keyCode) {
        if (this.textField.isFocused()) {
            boolean handled = this.textField.keyPressed(keyCode);
            if (!handled && character != 0) {
                this.textField.charTyped(character);
            }
            super.onKeyTyped(character, keyCode);
            return true;
        }

        return super.onKeyTyped(character, keyCode);
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        return this.textField.mouseClicked(this.getX() + relativeX, this.getY() + relativeY, mouseButton);
    }

    public TextFieldWidget setDisabledTextColor(int color) {
        this.textField.setDisabledTextColour(color);
        return this;
    }

    public TextFieldWidget setFocused(boolean focused) {
        this.textField.setFocused(focused);
        return this;
    }

    public boolean isFocused() {
        return this.textField.isFocused();
    }

    public TextFieldWidget setEnabled(boolean enabled) {
        this.textField.setEnabled(enabled);
        return this;
    }

    public boolean isEnabled() {
        return this.textField.isEnabled();
    }

    public String getText() {
        return this.textField.getText();
    }

    public TextFieldWidget setText(String text) {
        this.textField.setText(text);
        return this;
    }

    public TextFieldWidget setPlaceholder(String placeholder) {
        this.textField.setPlaceholder(placeholder);
        return this;
    }

    public TextFieldWidget drawUnderline(boolean drawUnderline) {
        this.textField.drawUnderline(drawUnderline);
        return this;
    }

    public TextFieldWidget setTextChangedCallback(TextField.TextChangedCallback callback) {
        this.textField.setTextChangedCallback(callback);
        return this;
    }
}
