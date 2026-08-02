package tritium.music.client.screens.widget;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.util.Mth;

import java.awt.Color;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ColorPickerWidget extends AbstractWidget<ColorPickerWidget> {

    private static final double WIDTH = 280;
    private static final double HEIGHT = 112;
    private static final double STRIP_WIDTH = 14;
    private static final double PREVIEW_WIDTH = 54;
    private static final double GAP = 6;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final boolean withAlpha;
    private final CFontRenderer font = FontManager.pf12bold;

    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private int dragging = -1;

    public ColorPickerWidget(IntSupplier getter, IntConsumer setter, boolean withAlpha) {
        this.getter = getter;
        this.setter = setter;
        this.withAlpha = withAlpha;
        setBounds(WIDTH, HEIGHT);
        setShouldOverrideMouseCursor(true);
        syncFromColor();
    }

    private void syncFromColor() {
        int color = getter.getAsInt();
        alpha = color >>> 24;
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (dragging >= 0) {
            if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                updateDrag(mouseX, mouseY);
            } else {
                dragging = -1;
            }
        }

        double x = getX();
        double y = getY();
        double sbWidth = saturationBrightnessWidth();
        int hueColor = withWidgetAlpha(RGBA.opaque(Color.HSBtoRGB(hue, 1f, 1f)));

        RenderSystem.drawGradientRectLeftToRight(
                x, y, x + sbWidth, y + getHeight(), reAlpha(0xFFFFFFFF, getAlpha()), hueColor);
        RenderSystem.drawGradientRectTopToBottom(
                x, y, x + sbWidth, y + getHeight(), RGBA.color(0, 0, 0, 0), reAlpha(0xFF000000, getAlpha()));
        RenderSystem.drawOutLine(x, y, sbWidth, getHeight(), 1, reAlpha(0xFF000000, getAlpha() * 0.55f));

        ring(x + saturation * sbWidth, y + (1f - brightness) * getHeight());
        renderHueStrip(hueX(), y);
        if (withAlpha) {
            renderAlphaStrip(alphaX(), y);
        }
        renderPreview(previewX(), y);
    }

    private void renderHueStrip(double x, double y) {
        int segments = 24;
        for (int index = 0; index < segments; index++) {
            float startHue = index / (float) segments;
            float endHue = (index + 1) / (float) segments;
            double startY = y + getHeight() * index / segments;
            double endY = y + getHeight() * (index + 1) / segments;
            RenderSystem.drawGradientRectTopToBottom(
                    x,
                    startY,
                    x + STRIP_WIDTH,
                    endY,
                    withWidgetAlpha(RGBA.opaque(Color.HSBtoRGB(startHue, 1f, 1f))),
                    withWidgetAlpha(RGBA.opaque(Color.HSBtoRGB(endHue, 1f, 1f))));
        }
        RenderSystem.drawOutLine(x, y, STRIP_WIDTH, getHeight(), 1, reAlpha(0xFF000000, getAlpha() * 0.55f));
        marker(x, y + hue * getHeight());
    }

    private void renderAlphaStrip(double x, double y) {
        checkerboard(x, y, STRIP_WIDTH, getHeight(), 4);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        RenderSystem.drawGradientRectTopToBottom(
                x,
                y,
                x + STRIP_WIDTH,
                y + getHeight(),
                RGBA.color(rgb, Math.round(255 * getAlpha())),
                RGBA.color(rgb, 0));
        RenderSystem.drawOutLine(x, y, STRIP_WIDTH, getHeight(), 1, reAlpha(0xFF000000, getAlpha() * 0.55f));
        marker(x, y + (1f - alpha / 255f) * getHeight());
    }

    private void renderPreview(double x, double y) {
        checkerboard(x, y, PREVIEW_WIDTH, PREVIEW_WIDTH, 6);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        Rect.draw(x, y, PREVIEW_WIDTH, PREVIEW_WIDTH, RGBA.color(rgb, Math.round(alpha * getAlpha())));
        RenderSystem.drawOutLine(x, y, PREVIEW_WIDTH, PREVIEW_WIDTH, 1, reAlpha(0xFFFFFFFF, getAlpha() * 0.45f));

        String hex = String.format(Locale.ROOT, "#%06X", rgb);
        double hexY = y + PREVIEW_WIDTH + 8;
        font.drawCenteredString(hex, x + PREVIEW_WIDTH * 0.5, hexY, reAlpha(0xFFF2F3F5, getAlpha()));
        if (withAlpha) {
            String opacity = Math.round(alpha / 255f * 100) + "%";
            font.drawCenteredString(
                    opacity,
                    x + PREVIEW_WIDTH * 0.5,
                    hexY + font.getStringHeight(hex) + 4,
                    reAlpha(0xFFB8BBC2, getAlpha()));
        }
    }

    private void checkerboard(double x, double y, double width, double height, double tile) {
        int columns = (int) Math.ceil(width / tile);
        int rows = (int) Math.ceil(height / tile);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double tileX = x + column * tile;
                double tileY = y + row * tile;
                double tileWidth = Math.min(tile, x + width - tileX);
                double tileHeight = Math.min(tile, y + height - tileY);
                int color = (row + column) % 2 == 0 ? 0xFFB8BBC2 : 0xFF73767D;
                Rect.draw(tileX, tileY, tileWidth, tileHeight, reAlpha(color, getAlpha()));
            }
        }
    }

    private void ring(double centerX, double centerY) {
        double radius = 4;
        RenderSystem.drawOutLine(
                centerX - radius - 1,
                centerY - radius - 1,
                radius * 2 + 2,
                radius * 2 + 2,
                1,
                reAlpha(0xFF000000, getAlpha() * 0.8f));
        RenderSystem.drawOutLine(
                centerX - radius,
                centerY - radius,
                radius * 2,
                radius * 2,
                1,
                reAlpha(0xFFFFFFFF, getAlpha()));
    }

    private void marker(double stripX, double y) {
        Rect.draw(stripX - 2, y - 2, STRIP_WIDTH + 4, 4, reAlpha(0xFF000000, getAlpha() * 0.85f));
        Rect.draw(stripX - 1, y - 1, STRIP_WIDTH + 2, 2, reAlpha(0xFFFFFFFF, getAlpha()));
    }

    @Override
    public boolean onMouseClicked(double relativeX, double relativeY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (inside(relativeX, relativeY, 0, 0, saturationBrightnessWidth(), getHeight())) {
            dragging = 0;
        } else if (inside(relativeX, relativeY, hueX() - getX(), 0, STRIP_WIDTH, getHeight())) {
            dragging = 1;
        } else if (withAlpha && inside(relativeX, relativeY, alphaX() - getX(), 0, STRIP_WIDTH, getHeight())) {
            dragging = 2;
        } else {
            return false;
        }
        updateDrag(getX() + relativeX, getY() + relativeY);
        return true;
    }

    private void updateDrag(double mouseX, double mouseY) {
        double x = getX();
        double y = getY();
        switch (dragging) {
            case 0 -> {
                saturation = (float) Mth.limit((mouseX - x) / saturationBrightnessWidth(), 0, 1);
                brightness = (float) (1 - Mth.limit((mouseY - y) / getHeight(), 0, 1));
            }
            case 1 -> hue = (float) Mth.limit((mouseY - y) / getHeight(), 0, 1);
            case 2 -> alpha = (int) Math.round(255 * (1 - Mth.limit((mouseY - y) / getHeight(), 0, 1)));
            default -> {
                return;
            }
        }
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        setter.accept((alpha << 24) | rgb);
    }

    private double saturationBrightnessWidth() {
        double alphaWidth = withAlpha ? STRIP_WIDTH + GAP : 0;
        return getWidth() - STRIP_WIDTH - GAP - alphaWidth - PREVIEW_WIDTH - GAP;
    }

    private double hueX() {
        return getX() + saturationBrightnessWidth() + GAP;
    }

    private double alphaX() {
        return hueX() + STRIP_WIDTH + GAP;
    }

    private double previewX() {
        return (withAlpha ? alphaX() + STRIP_WIDTH : hueX() + STRIP_WIDTH) + GAP;
    }

    private int withWidgetAlpha(int color) {
        return RGBA.color(color, Math.round(255 * getAlpha()));
    }

    private static boolean inside(double x, double y, double areaX, double areaY, double width, double height) {
        return x >= areaX && x <= areaX + width && y >= areaY && y <= areaY + height;
    }
}
