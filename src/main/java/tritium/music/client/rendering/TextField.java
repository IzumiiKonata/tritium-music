package tritium.music.client.rendering;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.font.CFontRenderer;

public class TextField {

    public interface TextChangedCallback {
        void onTextChanged(String text);
    }

    public float xPosition, yPosition, width, height;
    public int enabledColor = 0xFFFFFFFF;

    private CFontRenderer fontRenderer;
    private String text = "";
    private String placeholder = "";
    private boolean focused = false;
    private boolean enabled = true;
    private boolean drawUnderline = true;
    private int disabledColor = 0xFF707070;
    private int cursorPosition = 0;
    private float wholeAlpha = 1f;
    private TextChangedCallback textChangedCallback;
    private int maxLength = 256;

    public TextField setFontRenderer(CFontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
        return this;
    }

    public void setWholeAlpha(float alpha) {
        this.wholeAlpha = alpha;
    }

    public String getText() {
        return text;
    }

    public TextField setText(String text) {
        this.text = text == null ? "" : text;
        this.cursorPosition = Math.min(this.cursorPosition, this.text.length());
        notifyChanged();
        return this;
    }

    public TextField setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public boolean isFocused() {
        return focused;
    }

    public TextField setFocused(boolean focused) {
        this.focused = focused;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TextField setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public TextField drawUnderline(boolean drawUnderline) {
        this.drawUnderline = drawUnderline;
        return this;
    }

    public TextField setDisabledTextColour(int color) {
        this.disabledColor = color;
        return this;
    }

    public TextField setTextChangedCallback(TextChangedCallback callback) {
        this.textChangedCallback = callback;
        return this;
    }

    private void notifyChanged() {
        if (textChangedCallback != null) {
            textChangedCallback.onTextChanged(text);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= xPosition && mouseX <= xPosition + width
                && mouseY >= yPosition && mouseY <= yPosition + height;
        this.focused = inside && enabled;
        return inside;
    }

    private static boolean isCtrlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public void charTyped(char character) {
        if (!focused || !enabled) {
            return;
        }
        if (character >= 32 && character != 127 && text.length() < maxLength) {
            text = text.substring(0, cursorPosition) + character + text.substring(cursorPosition);
            cursorPosition++;
            notifyChanged();
        }
    }

    public boolean keyPressed(int key) {
        if (!focused || !enabled) {
            return false;
        }

        if (isCtrlDown()) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    cursorPosition = text.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().handle(), text);
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clip = GLFW.glfwGetClipboardString(Minecraft.getInstance().getWindow().handle());
                    if (clip != null) {
                        insert(clip);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().handle(), text);
                    setText("");
                    cursorPosition = 0;
                    return true;
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorPosition > 0) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    cursorPosition--;
                    notifyChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorPosition < text.length()) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                    notifyChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPosition < text.length()) cursorPosition++;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = text.length();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void insert(String s) {
        String filtered = s.replaceAll("[\\r\\n]", "");
        int allowed = maxLength - text.length();
        if (allowed <= 0) {
            return;
        }
        if (filtered.length() > allowed) {
            filtered = filtered.substring(0, allowed);
        }
        text = text.substring(0, cursorPosition) + filtered + text.substring(cursorPosition);
        cursorPosition += filtered.length();
        notifyChanged();
    }

    public void drawTextBox(int mouseX, int mouseY) {
        if (fontRenderer == null) {
            return;
        }

        int color = enabled ? enabledColor : disabledColor;
        color = RGBA.color(RGBA.red(color), RGBA.green(color), RGBA.blue(color), (int) (wholeAlpha * 255));

        double textY = yPosition + (height - fontRenderer.getFontHeight()) * 0.5;

        if (text.isEmpty() && !focused) {
            int phColor = RGBA.color(0x70, 0x70, 0x70, (int) (wholeAlpha * 255));
            fontRenderer.drawString(placeholder, xPosition, textY, phColor);
        } else {
            String shown = text;
            fontRenderer.drawString(shown, xPosition, textY, color);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                double caretX = xPosition + fontRenderer.getStringWidthD(text.substring(0, cursorPosition));
                Rect.draw(caretX, textY, 1, fontRenderer.getFontHeight(), color);
            }
        }

        if (drawUnderline) {
            Rect.draw(xPosition, yPosition + height, width, 1, RGBA.color(0xFF, 0xFF, 0xFF, (int) (wholeAlpha * 60)));
        }
    }
}
