package tritium.music.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class GuiStateReset {

    private GuiStateReset() {
    }

    public static void restore() {
        RenderSystem.disableScissor();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableCull();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }
}
