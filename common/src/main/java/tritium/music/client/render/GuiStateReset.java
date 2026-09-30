package tritium.music.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class GuiStateReset {

    private static final Logger LOGGER = LoggerFactory.getLogger("tritium-music/state");

    private static final Map<String, String> REPORTED = new HashMap<>();

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

    public static void audit(String origin) {
        report(origin, deviation());
        restore();
    }

    public static void auditFramebuffer(String origin) {
        report(origin, framebufferDeviation());
        restoreFramebuffer();
    }

    private static void restoreFramebuffer() {
        RenderSystem.disableScissor();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
    }

    private static void report(String origin, String deviation) {
        if (deviation == null) {
            REPORTED.remove(origin);
            return;
        }
        if (!deviation.equals(REPORTED.put(origin, deviation))) {
            LOGGER.warn("[state] repaired gl state after {}:{}", origin, deviation);
        }
    }

    private static String deviation() {
        StringBuilder builder = new StringBuilder();
        appendScissor(builder);
        if (!GL11.glGetBoolean(GL11.GL_DEPTH_TEST)) {
            builder.append(" depthTest=off");
        }
        appendDepthMask(builder);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        if (depthFunc != GL11.GL_LEQUAL) {
            builder.append(" depthFunc=").append(depthFunc);
        }
        if (!GL11.glGetBoolean(GL11.GL_CULL_FACE)) {
            builder.append(" cull=off");
        }
        appendColorMask(builder);
        return builder.isEmpty() ? null : builder.toString();
    }

    private static String framebufferDeviation() {
        StringBuilder builder = new StringBuilder();
        appendScissor(builder);
        appendDepthMask(builder);
        appendColorMask(builder);
        return builder.isEmpty() ? null : builder.toString();
    }

    private static void appendScissor(StringBuilder builder) {
        if (GL11.glGetBoolean(GL11.GL_SCISSOR_TEST)) {
            builder.append(" scissor=on");
        }
    }

    private static void appendDepthMask(StringBuilder builder) {
        if (!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)) {
            builder.append(" depthMask=off");
        }
    }

    private static void appendColorMask(StringBuilder builder) {
        int[] colorMask = new int[4];
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
        if (colorMask[0] == 0 || colorMask[1] == 0 || colorMask[2] == 0 || colorMask[3] == 0) {
            builder.append(" colorMask=").append(colorMask[0]).append(colorMask[1]).append(colorMask[2]).append(colorMask[3]);
        }
    }
}
