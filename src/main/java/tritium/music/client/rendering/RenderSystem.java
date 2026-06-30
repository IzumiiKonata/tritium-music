package tritium.music.client.rendering;

import com.mojang.blaze3d.platform.Window;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;

import java.awt.Color;

public class RenderSystem {

    public static final Object ASYNC_LOCK = new Object();
    public static final float DIVIDE_BY_255 = 0.003921568627451F;
    public static final Minecraft mc = Minecraft.getInstance();
    private static final double TARGET_GUI_SCALE = 2.0;
    private static final double REFERENCE_WIDTH = 1920.0;
    private static final double REFERENCE_HEIGHT = 1013.0;

    @Getter
    @Setter
    private static double frameDeltaTime = 0;

    private static Window window() {
        return mc.getWindow();
    }

    private static GuiGraphicsExtractor g() {
        return RenderContext.graphics();
    }

    public static double getScaleFactor() {
        return window().getGuiScaledWidth() == 0
                ? 1
                : (double) window().getWidth() / window().getGuiScaledWidth();
    }

    public static double getScaleNormalizer() {
        double xRatio = window().getGuiScaledWidth() / getWidth();
        double yRatio = window().getGuiScaledHeight() / getHeight();
        return Math.min(xRatio, yRatio);
    }

    public static double getOffsetX() {
        return (window().getGuiScaledWidth() - getWidth() * getScaleNormalizer()) / 2.0;
    }

    public static double getOffsetY() {
        return (window().getGuiScaledHeight() - getHeight() * getScaleNormalizer()) / 2.0;
    }

    public static double getFullBleedX() {
        return -getOffsetX() / getScaleNormalizer();
    }

    public static double getFullBleedY() {
        return -getOffsetY() / getScaleNormalizer();
    }

    public static double getFullBleedWidth() {
        return window().getGuiScaledWidth() / getScaleNormalizer();
    }

    public static double getFullBleedHeight() {
        return window().getGuiScaledHeight() / getScaleNormalizer();
    }

    public static double getWidth() {
        return REFERENCE_WIDTH / TARGET_GUI_SCALE;
    }

    public static double getHeight() {
        return REFERENCE_HEIGHT / TARGET_GUI_SCALE;
    }

    public static double getFixedWidth() {
        return getWidth();
    }

    public static double getFixedHeight() {
        return getHeight();
    }

    public static double getMouseX() {
        double guiX = mc.mouseHandler.xpos() * window().getGuiScaledWidth() / window().getWidth();
        return (guiX - getOffsetX()) / getScaleNormalizer();
    }

    public static double getMouseY() {
        double guiY = mc.mouseHandler.ypos() * window().getGuiScaledHeight() / window().getHeight();
        return (guiY - getOffsetY()) / getScaleNormalizer();
    }

    public static void color(int color) {
    }

    public static void resetColor() {
    }

    private static @Nullable Identifier boundTexture = null;

    public static void bindTexture(@Nullable Identifier texture) {
        boundTexture = texture;
    }

    public static @Nullable Identifier boundTexture() {
        return boundTexture;
    }

    public static int hexColor(int r, int g, int b, int a) {
        return RGBA.color(r, g, b, a);
    }

    public static int hexColor(int r, int g, int b) {
        return RGBA.color(r, g, b, 255);
    }

    public static int hexColor(float r, float g, float b, float a) {
        return RGBA.color(r, g, b, a);
    }

    public static int hexColor(float r, float g, float b) {
        return RGBA.color(r, g, b, 1.0f);
    }

    public static void drawRect(double left, double top, double right, double bottom, int color) {
        if (left > right) {
            double i = left;
            left = right;
            right = i;
        }
        if (top > bottom) {
            double j = top;
            top = bottom;
            bottom = j;
        }
        if (StencilClipManager.capturing()) {
            StencilClipManager.captureRect(left, top, right - left, bottom - top);
            return;
        }
        Render.rect(g(), (float) left, (float) top, (float) (right - left), (float) (bottom - top), color);
    }

    public static void drawGradientRectLeftToRight(double left, double top, double right, double bottom, int startColor, int endColor) {
        Render.gradientH(g(), (float) left, (float) top, (float) (right - left), (float) (bottom - top), startColor, endColor);
    }

    public static void drawGradientRectTopToBottom(double left, double top, double right, double bottom, int startColor, int endColor) {
        Render.gradientV(g(), (float) left, (float) top, (float) (right - left), (float) (bottom - top), startColor, endColor);
    }

    public static void drawGradientRectBottomToTop(double left, double top, double right, double bottom, int startColor, int endColor) {
        Render.gradientV(g(), (float) left, (float) top, (float) (right - left), (float) (bottom - top), endColor, startColor);
    }

    public static void drawOutLine(double x, double y, double width, double height, double thickness, int color) {
        Rect.draw(x - thickness, y - thickness, width + thickness * 2, thickness, color);
        Rect.draw(x - thickness, y - thickness, thickness, height + thickness, color);
        Rect.draw(x + width, y - thickness, thickness, height + thickness, color);
        Rect.draw(x - thickness, y + height, width + thickness * 2, thickness, color);
    }

    public static boolean isHovered(double mouseX, double mouseY, double startX, double startY, double width, double height) {
        if (width < 0) {
            width = -width;
            startX -= width;
        }
        if (height < 0) {
            height = -height;
            startY -= height;
        }
        return mouseX >= startX && mouseY >= startY && mouseX <= startX + width && mouseY <= startY + height;
    }

    public static boolean isHovered(double mouseX, double mouseY, double startX, double startY, double width, double height, double shrink) {
        return isHovered(mouseX, mouseY, startX + shrink, startY + shrink, width - shrink * 2, height - shrink * 2);
    }

    public static void translateAndScale(double posX, double posY, double scale) {
        g().pose().translate((float) posX, (float) posY);
        g().pose().scale((float) scale, (float) scale);
        g().pose().translate((float) -posX, (float) -posY);
    }

    public static void doScissor(double x, double y, double width, double height) {
        doScissor((int) x, (int) y, (int) width, (int) height);
    }

    public static void doScissor(double x, double y, double width, double height, double shrink) {
        doScissor(x - shrink, y - shrink, width + shrink * 2, height + shrink * 2);
    }

    public static volatile boolean forceDisableScissor = false;

    public static void doScissor(int x, int y, int width, int height) {
        if (forceDisableScissor) {
            return;
        }
        g().enableScissor(x, y, x + width, y + height);
    }

    public static void endScissor() {
        if (forceDisableScissor) {
            return;
        }
        g().disableScissor();
    }

    public static Color getOppositeColor(Color colorIn) {
        return new Color(255 - colorIn.getRed(), 255 - colorIn.getGreen(), 255 - colorIn.getBlue(), colorIn.getAlpha());
    }

    public static int getOppositeColorHex(int colorHex) {
        return getOppositeColor(new Color(colorHex, true)).getRGB();
    }

    public static int cRange(int c) {
        if (c < 0) {
            c = 0;
        }
        if (c > 255) {
            c = 255;
        }
        return c;
    }

    public static int reAlpha(int color, float alpha) {
        if (alpha > 1) {
            alpha = 1;
        }
        if (alpha < 0) {
            alpha = 0;
        }
        return RGBA.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, (color) & 0xFF, (int) (alpha * 255));
    }
}
