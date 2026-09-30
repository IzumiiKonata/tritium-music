package tritium.music.client.rendering;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public final class TRenderTarget implements AutoCloseable {

    private final String name;
    private TextureTarget target;
    private int width;
    private int height;

    private TRenderTarget(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.target = createTarget(width, height);
        rebindMainTarget();
    }

    public static TRenderTarget create(String name, int width, int height) {
        return new TRenderTarget(name, width, height);
    }

    private static TextureTarget createTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, false);
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = newWidth;
        height = newHeight;
        target.resize(newWidth, newHeight, false);
        target.setFilterMode(GL11.GL_LINEAR);
        rebindMainTarget();
    }

    public void bindWrite() {
        target.bindWrite(true);
    }

    public void clear() {
        target.bindWrite(true);
        GlStateManager._clearColor(0f, 0f, 0f, 0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, false);
    }

    public int colorTextureId() {
        return target.getColorTextureId();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String name() {
        return name;
    }

    public static void rebindMainTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main != null) {
            main.bindWrite(true);
        }
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
            width = 0;
            height = 0;
        }
    }
}
