package tritium.music.fabric.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import tritium.music.platform.TextureHandle;

public final class Draw {

    private Draw() {
    }

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    public static void gradientV(GuiGraphicsExtractor g, float x, float y, float w, float h, int top, int bottom) {
        g.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), top, bottom);
    }

    /**
     * Rounded rectangle approximated with horizontal spans — no shader required.
     */
    public static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        int xi = Math.round(x), yi = Math.round(y), wi = Math.round(w), hi = Math.round(h);
        int r = Math.round(Math.min(radius, Math.min(wi, hi) / 2f));
        if (r <= 0) {
            g.fill(xi, yi, xi + wi, yi + hi, color);
            return;
        }

        g.fill(xi, yi + r, xi + wi, yi + hi - r, color);

        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.floor(Math.sqrt(r * r - (r - i - 1) * (r - i - 1)));
            int rowTop = yi + i;
            int rowBottom = yi + hi - 1 - i;
            g.fill(xi + inset, rowTop, xi + wi - inset, rowTop + 1, color);
            g.fill(xi + inset, rowBottom, xi + wi - inset, rowBottom + 1, color);
        }
    }

    public static void texture(GuiGraphicsExtractor g, TextureHandle handle, float x, float y, float w, float h, float alpha) {
        texture(g, Identifiers.of(handle), x, y, w, h, alpha);
    }

    public static void texture(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, float alpha) {
        int a = (int) (Ease.clamp01(alpha) * 255f) & 0xFF;
        int color = (a << 24) | 0xFFFFFF;
        int xi = Math.round(x), yi = Math.round(y), wi = Math.round(w), hi = Math.round(h);
        g.blit(RenderPipelines.GUI_TEXTURED, id, xi, yi, 0f, 0f, wi, hi, wi, hi, wi, hi, color);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String str, float x, float y, int color) {
        g.text(font, str, Math.round(x), Math.round(y), color, true);
    }

    public static void textNoShadow(GuiGraphicsExtractor g, Font font, String str, float x, float y, int color) {
        g.text(font, str, Math.round(x), Math.round(y), color, false);
    }

    public static String trim(Font font, String str, int maxWidth) {
        if (font.width(str) <= maxWidth) {
            return str;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            int cw = font.width(String.valueOf(str.charAt(i)));
            if (width + cw + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(str.charAt(i));
            width += cw;
        }
        return sb.append(ellipsis).toString();
    }
}
