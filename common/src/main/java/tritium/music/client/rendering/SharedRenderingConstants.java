package tritium.music.client.rendering;

import net.minecraft.resources.Identifier;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public interface SharedRenderingConstants {

    default void roundedRect(double x, double y, double width, double height, double radius, float r, float g, float b, float a) {
        roundedRect(x, y, width, height, radius, RGBA.color(r, g, b, a));
    }

    default void roundedRect(double x, double y, double width, double height, double radius, double expand, float r, float g, float b, float a) {
        roundedRect(x - expand, y - expand, width + expand * 2, height + expand * 2, radius, r, g, b, a);
    }

    default void roundedRect(double x, double y, double width, double height, double radius, int r, int g, int b, int a) {
        roundedRect(x, y, width, height, radius, RGBA.color(r, g, b, a));
    }

    default void roundedRect(double x, double y, double width, double height, double radius, int color) {
        Render.roundedRect(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height, (float) radius, color);
    }

    default void roundedRect(double x, double y, double width, double height, double radius, Color color) {
        roundedRect(x, y, width, height, radius, color.getRGB());
    }

    default void roundedRect(double x, double y, double width, double height, double radius, double expand, Color color) {
        roundedRect(x - expand, y - expand, width + expand * 2, height + expand * 2, radius, color.getRGB());
    }

    default void roundedOutline(double x, double y, double width, double height, double radius, double thickness, Color outline) {
        Render.roundedOutline(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, (float) thickness, outline.getRGB());
    }

    default void roundedOutline(double x, double y, double width, double height, double radius, double thickness, double expand, Color outline) {
        this.roundedOutline(x - expand, y - expand, width + expand * 2, height + expand * 2, radius, thickness, outline);
    }

    default void roundedOutlineGradient(double x, double y, double width, double height, double radius, double thickness, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        Render.roundedOutlineGradient(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, (float) thickness, bottomLeft.getRGB(), topLeft.getRGB(), bottomRight.getRGB(), topRight.getRGB());
    }

    default void roundedRectTextured(double x, double y, double width, double height, double radius) {
        roundedRectTextured(x, y, width, height, radius, 1f);
    }

    default void roundedRectTextured(double x, double y, double width, double height, double radius, float alpha) {
        Identifier texture = RenderSystem.boundTexture();
        if (texture == null) {
            return;
        }
        Render.roundedTexture(RenderContext.graphics(), texture, (float) x, (float) y, (float) width, (float) height, (float) radius, alpha);
    }

    default void roundedRectTextured(double x, double y, double width, double height, double texX, double texY, double u, double v, double radius) {
        roundedRectTextured(x, y, width, height, texX, texY, u, v, radius, 1f);
    }

    default void roundedRectTextured(double x, double y, double width, double height, double texX, double texY, double u, double v, double radius, float alpha) {
        Identifier texture = RenderSystem.boundTexture();
        if (texture == null) {
            return;
        }
        Render.roundedTexture(RenderContext.graphics(), texture, (float) x, (float) y, (float) width, (float) height, (float) radius, alpha,
                (float) texX, (float) texY, (float) u, (float) v);
    }

    default void roundedRectTextured(double x, double y, double width, double height, double texX, double texY, double u, double v, double radius, double expand, float alpha) {
        Identifier texture = RenderSystem.boundTexture();
        if (texture == null) {
            return;
        }
        Render.roundedTextureSpecial(RenderContext.graphics(), texture, (float) (x - expand), (float) (y - expand),
                (float) (width + expand * 2), (float) (height + expand * 2), (float) radius, alpha,
                (float) texX, (float) texY, (float) u, (float) v);
    }

    default void roundedRectGradientHorizontal(double x, double y, double width, double height, double radius, Color left, Color right) {
        Render.roundedGradient(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, left.getRGB(), left.getRGB(), right.getRGB(), right.getRGB());
    }

    default void roundedRectGradientVertical(double x, double y, double width, double height, double radius, Color top, Color bottom) {
        Render.roundedGradient(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, bottom.getRGB(), top.getRGB(), bottom.getRGB(), top.getRGB());
    }

    default void drawGradientCornerLR(double x, double y, double width, double height, double radius, Color topLeft, Color bottomRight) {
        Color mixed = evenAdd(topLeft, bottomRight);
        Render.roundedGradient(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, mixed.getRGB(), topLeft.getRGB(), bottomRight.getRGB(), mixed.getRGB());
    }

    default void drawGradientCornerRL(double x, double y, double width, double height, double radius, Color bottomLeft, Color topRight) {
        Color mixed = evenAdd(bottomLeft, topRight);
        Render.roundedGradient(RenderContext.graphics(), (float) x, (float) y, (float) width, (float) height,
                (float) radius, bottomLeft.getRGB(), mixed.getRGB(), mixed.getRGB(), topRight.getRGB());
    }

    default Color evenAdd(Color a, Color b) {
        return new Color(a.getRed() / 2 + b.getRed() / 2, a.getGreen() / 2 + b.getGreen() / 2, a.getBlue() / 2 + b.getBlue() / 2);
    }

    default double getWidth() {
        return RenderSystem.getWidth();
    }

    default double getHeight() {
        return RenderSystem.getHeight();
    }

    default int hexColor(int red, int green, int blue) {
        return RGBA.color(red, green, blue);
    }

    default int hexColor(int red, int green, int blue, int alpha) {
        return RGBA.color(red, green, blue, alpha);
    }

    default int hexColor(float r, float g, float b) {
        return RGBA.color(r, g, b);
    }

    default int hexColor(float r, float g, float b, float a) {
        return RGBA.color(r, g, b, a);
    }

    default int reAlpha(int color, float alpha) {
        if (alpha > 1) {
            alpha = 1;
        }
        if (alpha < 0) {
            alpha = 0;
        }
        return RGBA.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, (color) & 0xFF, (int) (alpha * 255));
    }

    default boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return RenderSystem.isHovered(mouseX, mouseY, x, y, width, height);
    }

    default void bloomAndBlur(Runnable r) {
        BLOOM.add(r);
        BLUR.add(r);
    }

    default void matrix(Runnable render) {
        RenderContext.graphics().pose().pushMatrix();
        render.run();
        RenderContext.graphics().pose().popMatrix();
    }

    default void scaleAtPos(double posX, double posY, double scale) {
        var pose = RenderContext.graphics().pose();
        pose.translate((float) posX, (float) posY);
        pose.scale((float) scale, (float) scale);
        pose.translate((float) -posX, (float) -posY);
    }

    default void rotateAtPos(double posX, double posY, float rotate) {
        var pose = RenderContext.graphics().pose();
        pose.translate((float) posX, (float) posY);
        pose.rotate((float) Math.toRadians(rotate));
        pose.translate((float) -posX, (float) -posY);
    }

    static void clearRunnables() {
        BLUR.clear();
        BLOOM.clear();
    }

    List<Runnable> BLUR = new ArrayList<>();
    List<Runnable> BLOOM = new ArrayList<>();
}
