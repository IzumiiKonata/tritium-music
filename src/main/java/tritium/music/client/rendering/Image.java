package tritium.music.client.rendering;

import net.minecraft.resources.Identifier;
import tritium.music.client.render.Render;
import tritium.music.client.render.RenderContext;
import tritium.music.fabric.ui.Identifiers;
import tritium.music.platform.TextureHandle;

public class Image {

    public static void draw(TextureHandle img, double x, double y, double width, double height) {
        draw(img, x, y, width, height, Type.Normal);
    }

    public static void draw(TextureHandle img, double x, double y, double width, double height, Type type) {
        blit(Identifiers.of(img), x, y, width, height, 0f, 0f, 1f, 1f, 1f);
    }

    public static void draw(TextureHandle img, double x, double y, double width, double height, Type type, float alpha) {
        blit(Identifiers.of(img), x, y, width, height, 0f, 0f, 1f, 1f, alpha);
    }

    public static void drawRegion(TextureHandle img, double x, double y, double width, double height,
                                  double sourceX, double sourceY, double sourceWidth, double sourceHeight,
                                  double textureWidth, double textureHeight, float alpha) {
        float u0 = (float) (sourceX / textureWidth);
        float v0 = (float) (sourceY / textureHeight);
        float u1 = (float) ((sourceX + sourceWidth) / textureWidth);
        float v1 = (float) ((sourceY + sourceHeight) / textureHeight);
        blit(Identifiers.of(img), x, y, width, height, u0, v0, u1, v1, alpha);
    }

    public static void drawLinear(TextureHandle img, double x, double y, double width, double height, Type type) {
        draw(img, x, y, width, height, type);
    }

    public static void drawNearest(TextureHandle img, double x, double y, double width, double height, Type type) {
        draw(img, x, y, width, height, type);
    }

    public static void drawLinearFlippedX(TextureHandle img, double x, double y, double width, double height, Type type) {
        blit(Identifiers.of(img), x, y, width, height, 1f, 0f, 0f, 1f, 1f);
    }

    public static void drawLinearFlippedY(TextureHandle img, double x, double y, double width, double height, Type type) {
        blit(Identifiers.of(img), x, y, width, height, 0f, 1f, 1f, 0f, 1f);
    }

    public static void drawLinearFlippedXAndY(TextureHandle img, double x, double y, double width, double height, Type type) {
        blit(Identifiers.of(img), x, y, width, height, 1f, 1f, 0f, 0f, 1f);
    }

    public static void draw(Identifier textureId, double x, double y, double width, double height, Type type) {
        blit(textureId, x, y, width, height, 0f, 0f, 1f, 1f, 1f);
    }

    private static void blit(Identifier id, double x, double y, double width, double height, float u0, float v0, float u1, float v1, float alpha) {
        Render.texture(RenderContext.graphics(), id, (float) x, (float) y, (float) width, (float) height, u0, v0, u1, v1, alpha);
    }

    public enum Type {
        NoColor, Normal
    }
}
