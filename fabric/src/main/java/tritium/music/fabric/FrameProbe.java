package tritium.music.fabric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.StencilClipManager;
import tritium.music.client.rendering.shader.EffectQueue;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class FrameProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("tritium-music/frame");

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int HISTORY = 720;
    private static final int DUMP_STEP = 8;

    private static final Deque<Record> HISTORY_BUFFER = new ArrayDeque<>();

    private static ByteBuffer row;
    private static int frames;
    private static boolean hideGuiKnown;
    private static boolean lastHideGui;

    private FrameProbe() {
    }

    public static void beginFrame() {
        frames++;
        Minecraft minecraft = Minecraft.getInstance();
        boolean hideGui = minecraft.options.hideGui;
        if (hideGuiKnown && hideGui != lastHideGui) {
            dump("hideGui " + lastHideGui + "->" + hideGui);
        }
        lastHideGui = hideGui;
        hideGuiKnown = true;
    }

    public static void sample(String stage) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0) {
            return;
        }
        if (row == null || row.capacity() < target.width * 4) {
            row = BufferUtils.createByteBuffer(target.width * 4);
        }
        target.bindRead();
        StringBuilder grid = new StringBuilder();
        int brightest = 0;
        for (int r = 0; r < ROWS; r++) {
            int y = Math.min(target.height - 1, Math.max(0, (int) ((r + 0.5f) * target.height / ROWS)));
            row.clear();
            GL11.glReadPixels(0, y, target.width, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, row);
            for (int c = 0; c < COLUMNS; c++) {
                int from = (int) ((long) target.width * c / COLUMNS);
                int to = (int) ((long) target.width * (c + 1) / COLUMNS);
                int max = 0;
                for (int x = from; x < to; x++) {
                    int offset = x * 4;
                    int value = Math.max(row.get(offset) & 0xFF, Math.max(row.get(offset + 1) & 0xFF, row.get(offset + 2) & 0xFF));
                    if (value > max) {
                        max = value;
                    }
                }
                if (max > brightest) {
                    brightest = max;
                }
                grid.append(String.format("%02x", max)).append(c == COLUMNS - 1 ? "" : " ");
            }
            grid.append(" / ");
        }
        HISTORY_BUFFER.addLast(new Record(frames, stage, brightest, grid.toString()));
        while (HISTORY_BUFFER.size() > HISTORY) {
            HISTORY_BUFFER.removeFirst();
        }
    }

    private static void dump(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        LOGGER.warn("[frame] dump trigger={} frame={} {}", reason, frames, state(minecraft));
        int index = 0;
        for (Iterator<Record> iterator = HISTORY_BUFFER.iterator(); iterator.hasNext(); index++) {
            Record record = iterator.next();
            if (index % DUMP_STEP != 0) {
                continue;
            }
            LOGGER.warn("[frame] f={} {} max={} {}", record.frame, record.stage, record.brightest, record.grid);
        }
    }

    private static String state(Minecraft minecraft) {
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        float[] clear = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        float[] color = RenderSystem.getShaderColor();
        float[] fog = RenderSystem.getShaderFogColor();
        WidgetConfig config = WidgetConfig.get();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int expected = minecraft.getTextureManager().getTexture(VIGNETTE).getId();
        return "shaderColor=" + fmt(color)
                + " fog=" + RenderSystem.getShaderFogStart() + ".." + RenderSystem.getShaderFogEnd() + " " + fmt(fog)
                + " clear=" + fmt(clear)
                + " tex0=" + bound + "/vignette=" + expected
                + " blend=" + GL11.glGetInteger(GL11.GL_BLEND_SRC) + ">" + GL11.glGetInteger(GL11.GL_BLEND_DST) + (GL11.glGetBoolean(GL11.GL_BLEND) ? " on" : " off")
                + " depth=" + (GL11.glGetBoolean(GL11.GL_DEPTH_TEST) ? "on" : "off") + "/" + (GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? "write" : "masked") + "/" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                + " cull=" + GL11.glGetBoolean(GL11.GL_CULL_FACE)
                + " scissor=" + GL11.glGetBoolean(GL11.GL_SCISSOR_TEST) + scissorBox[0] + "," + scissorBox[1] + "," + scissorBox[2] + "," + scissorBox[3]
                + " fbo=" + GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                + " screen=" + (minecraft.screen == null ? "-" : minecraft.screen.getClass().getSimpleName())
                + " hideGui=" + minecraft.options.hideGui
                + " chunks=" + (minecraft.level == null ? "-" : minecraft.levelRenderer.getChunkStatistics())
                + " fps=" + minecraft.getFps()
                + " widgets=" + config.musicInfo.enabled + "/" + config.musicLyrics.enabled + "/" + config.musicSpectrum.enabled
                + " clips=" + StencilClipManager.stencilClipping()
                + " blurs=" + EffectQueue.blurs().size() + " blooms=" + EffectQueue.blooms().size();
    }

    private static String fmt(float[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format("%.2f", values[i]));
        }
        return builder.toString();
    }

    private record Record(int frame, String stage, int brightest, String grid) {
    }
}
