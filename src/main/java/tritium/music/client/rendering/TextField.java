package tritium.music.client.rendering;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.util.Mth;
import tritium.music.client.util.MouseUtil;
import tritium.music.core.util.Timer;

import java.awt.Color;

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
    private int selectionEnd = 0;
    private int dragStartChar = 0;
    private int dragEndChar = 0;
    private boolean dragging = false;

    private float wholeAlpha = 1f;
    private TextChangedCallback textChangedCallback;
    private int maxLength = 256;

    private double animatedCursorX = -1;
    private final Timer cursorForceShowTimer = new Timer();

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
        setCursorPositionEnd();
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
        if (focused && !this.focused) {
            cursorForceShowTimer.reset();
        }
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

    public String getSelectedText() {
        int start = Math.min(cursorPosition, selectionEnd);
        int end = Math.max(cursorPosition, selectionEnd);
        return text.substring(start, end);
    }

    private boolean hasSelection() {
        return cursorPosition != selectionEnd;
    }

    public void setCursorPosition(int position) {
        cursorPosition = Mth.clamp(position, 0, text.length());
        selectionEnd = cursorPosition;
        dragStartChar = cursorPosition;
        dragEndChar = cursorPosition;
        cursorForceShowTimer.reset();
    }

    public void setCursorPositionEnd() {
        setCursorPosition(text.length());
    }

    public void setSelectionPos(int position) {
        selectionEnd = Mth.clamp(position, 0, text.length());
        dragEndChar = selectionEnd;
        cursorForceShowTimer.reset();
    }

    public void selectAll() {
        cursorPosition = 0;
        selectionEnd = text.length();
        dragStartChar = 0;
        dragEndChar = text.length();
    }

    private void deleteSelection() {
        int start = Math.min(cursorPosition, selectionEnd);
        int end = Math.max(cursorPosition, selectionEnd);
        text = text.substring(0, start) + text.substring(end);
        setCursorPosition(start);
        notifyChanged();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= xPosition && mouseX <= xPosition + width
                && mouseY >= yPosition && mouseY <= yPosition + height;
        this.setFocused(inside && enabled);

        if (focused && inside && button == 0) {
            int pos = charIndexAt(mouseX);
            setCursorPosition(pos);
            dragging = true;
        }
        return inside;
    }

    private int charIndexAt(double mouseX) {
        double relativeX = mouseX - xPosition;
        if (text.isEmpty() || relativeX <= 0) {
            return 0;
        }
        for (int i = 1; i <= text.length(); i++) {
            double w = fontRenderer.getStringWidthD(text.substring(0, i));
            double prev = fontRenderer.getStringWidthD(text.substring(0, i - 1));
            if (relativeX < prev + (w - prev) * 0.5) {
                return i - 1;
            }
        }
        return text.length();
    }

    private static boolean isCtrlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public void charTyped(char character) {
        if (!focused || !enabled) {
            return;
        }
        if (character >= 32 && character != 127 && text.length() < maxLength) {
            if (hasSelection()) {
                deleteSelection();
            }
            text = text.substring(0, cursorPosition) + character + text.substring(cursorPosition);
            setCursorPosition(cursorPosition + 1);
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
                    selectAll();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().handle(), getSelectedText());
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
                    GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().handle(), getSelectedText());
                    if (hasSelection()) {
                        deleteSelection();
                    }
                    return true;
                }
            }
        }

        boolean shift = isShiftDown();

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    setCursorPosition(cursorPosition - 1);
                    notifyChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < text.length()) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                    notifyChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                int target = hasSelection() && !shift ? Math.min(cursorPosition, selectionEnd) : Math.max(0, cursorPosition - 1);
                moveCursor(target, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                int target = hasSelection() && !shift ? Math.max(cursorPosition, selectionEnd) : Math.min(text.length(), cursorPosition + 1);
                moveCursor(target, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(text.length(), shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveCursor(int target, boolean keepSelection) {
        cursorPosition = Mth.clamp(target, 0, text.length());
        if (!keepSelection) {
            selectionEnd = cursorPosition;
            dragStartChar = cursorPosition;
        }
        dragEndChar = cursorPosition;
        cursorForceShowTimer.reset();
    }

    private void insert(String s) {
        String filtered = s.replaceAll("[\\r\\n]", "");
        if (hasSelection()) {
            deleteSelection();
        }
        int allowed = maxLength - text.length();
        if (allowed <= 0) {
            return;
        }
        if (filtered.length() > allowed) {
            filtered = filtered.substring(0, allowed);
        }
        text = text.substring(0, cursorPosition) + filtered + text.substring(cursorPosition);
        setCursorPosition(cursorPosition + filtered.length());
        notifyChanged();
    }

    public void drawTextBox(int mouseX, int mouseY) {
        if (fontRenderer == null) {
            return;
        }

        if (dragging && !MouseUtil.isLeftDown()) {
            dragging = false;
        }
        if (dragging) {
            int pos = charIndexAt(mouseX);
            cursorPosition = pos;
            dragEndChar = pos;
            selectionEnd = pos;
            cursorForceShowTimer.reset();
        }

        int color = enabled ? enabledColor : disabledColor;
        color = RGBA.color(RGBA.red(color), RGBA.green(color), RGBA.blue(color), (int) (wholeAlpha * 255));

        double textY = yPosition + (height - fontRenderer.getFontHeight()) * 0.5;

        if (text.isEmpty() && !focused) {
            int phColor = RGBA.color(0x70, 0x70, 0x70, (int) (wholeAlpha * 255));
            fontRenderer.drawString(placeholder, xPosition, textY, phColor);
        } else {
            if (hasSelection()) {
                renderSelection(textY);
            }

            fontRenderer.drawString(text, xPosition, textY, color);

            if (focused && !hasSelection()) {
                renderCursor(textY);
            }
        }

        if (drawUnderline) {
            Rect.draw(xPosition, yPosition + height, width, 1, RGBA.color(0xFF, 0xFF, 0xFF, (int) (wholeAlpha * 60)));
        }
    }

    private void renderSelection(double textY) {
        int low = Math.min(cursorPosition, selectionEnd);
        int high = Math.max(cursorPosition, selectionEnd);
        if (low >= high) {
            return;
        }

        double startX = xPosition + fontRenderer.getStringWidthD(text.substring(0, low));
        double endX = startX + fontRenderer.getStringWidthD(text.substring(low, high)) + 1;

        int sel = RGBA.color(new Color(196, 225, 245).getRGB(), (int) (wholeAlpha * 255));
        Rect.draw(startX, textY - 1, endX - startX, fontRenderer.getFontHeight() + 2, sel);
    }

    private void renderCursor(double textY) {
        double caretX = xPosition + fontRenderer.getStringWidthD(text.substring(0, cursorPosition));

        if (animatedCursorX < 0) {
            animatedCursorX = caretX;
        }
        animatedCursorX = Interpolations.interpolate(animatedCursorX, caretX, 0.4f);

        long l = System.currentTimeMillis() / 5 % 255;
        int pulse = (int) Math.min(255, (l > 127 ? (255 - l) : l) * 2);

        if (pulse > 127 || !cursorForceShowTimer.isDelayed(750)) {
            int caretColor = RGBA.color(0xCD, 0xCB, 0xCD, (int) (wholeAlpha * 255));
            Rect.draw(animatedCursorX, textY - 2, 0.7, fontRenderer.getFontHeight() + 4, caretColor);
        }
    }
}
