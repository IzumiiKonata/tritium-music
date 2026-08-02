package tritium.music.client.screens.widget;

import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.util.Mth;

import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Self-contained HSB colour picker: a saturation/brightness square + a hue strip
 * + an alpha strip. Reads/writes an ARGB int through the supplied accessors.
 */
public class ColorPickerWidget implements SharedRenderingConstants {

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final boolean withAlpha;

    private double x, y, width, height;

    private float hue, saturation, brightness;
    private int alpha;

    private int dragging = -1;

    private static final double HUE_W = 14;
    private static final double GAP = 6;

    public ColorPickerWidget(IntSupplier getter, IntConsumer setter, boolean withAlpha) {
        this.getter = getter;
        this.setter = setter;
        this.withAlpha = withAlpha;
        syncFromColor();
    }

    public void setBounds(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private void syncFromColor() {
        int color = getter.getAsInt();
        alpha = (color >>> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private void pushColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        setter.accept((alpha << 24) | rgb);
    }

    private double sbW() {
        double strips = HUE_W + GAP + (withAlpha ? HUE_W + GAP : 0);
        return width - strips;
    }

    private double hueX() {
        return x + sbW() + GAP;
    }

    private double alphaX() {
        return hueX() + HUE_W + GAP;
    }

    public void render(double mouseX, double mouseY) {
        if (dragging >= 0) {
            updateDrag(mouseX, mouseY);
        }

        double sbW = sbW();

        int hueColor = RGBA.opaque(Color.HSBtoRGB(hue, 1f, 1f));
        RenderSystem.drawGradientRectLeftToRight(x, y, x + sbW, y + height, RGBA.color(255, 255, 255, 255), hueColor);
        RenderSystem.drawGradientRectTopToBottom(x, y, x + sbW, y + height, RGBA.color(0, 0, 0, 0), RGBA.color(0, 0, 0, 255));

        double sx = x + saturation * sbW;
        double sy = y + (1f - brightness) * height;
        ring(sx, sy);

        double hx = hueX();
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float h0 = i / (float) segments;
            float h1 = (i + 1) / (float) segments;
            double y0 = y + height * i / segments;
            double y1 = y + height * (i + 1) / segments;
            RenderSystem.drawGradientRectTopToBottom(hx, y0, hx + HUE_W, y1,
                    RGBA.opaque(Color.HSBtoRGB(h0, 1f, 1f)), RGBA.opaque(Color.HSBtoRGB(h1, 1f, 1f)));
        }
        marker(hx, y + hue * height, HUE_W);

        if (withAlpha) {
            double ax = alphaX();
            int solid = RGBA.opaque(Color.HSBtoRGB(hue, saturation, brightness));
            RenderSystem.drawGradientRectTopToBottom(ax, y, ax + HUE_W, y + height, solid, solid & 0xFFFFFF);
            marker(ax, y + (1f - alpha / 255f) * height, HUE_W);
        }
    }

    private void ring(double cx, double cy) {
        double r = 3;
        RenderSystem.drawOutLine(cx - r, cy - r, r * 2, r * 2, 1, RGBA.color(255, 255, 255, 255));
        RenderSystem.drawOutLine(cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, 1, RGBA.color(0, 0, 0, 200));
    }

    private void marker(double stripX, double yPos, double w) {
        Rect.draw(stripX - 1, yPos - 1, w + 2, 2, RGBA.color(0, 0, 0, 220));
        Rect.draw(stripX, yPos - 0.5, w, 1, RGBA.color(255, 255, 255, 255));
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, x, y, sbW(), height)) {
            dragging = 0;
        } else if (inside(mouseX, mouseY, hueX(), y, HUE_W, height)) {
            dragging = 1;
        } else if (withAlpha && inside(mouseX, mouseY, alphaX(), y, HUE_W, height)) {
            dragging = 2;
        } else {
            return false;
        }
        updateDrag(mouseX, mouseY);
        return true;
    }

    public void mouseReleased() {
        dragging = -1;
    }

    private void updateDrag(double mouseX, double mouseY) {
        switch (dragging) {
            case 0 -> {
                saturation = (float) Mth.limit((mouseX - x) / sbW(), 0, 1);
                brightness = (float) (1.0 - Mth.limit((mouseY - y) / height, 0, 1));
            }
            case 1 -> hue = (float) Mth.limit((mouseY - y) / height, 0, 1);
            case 2 -> alpha = (int) (255 * (1.0 - Mth.limit((mouseY - y) / height, 0, 1)));
        }
        pushColor();
    }

    private boolean inside(double mx, double my, double rx, double ry, double rw, double rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }
}
