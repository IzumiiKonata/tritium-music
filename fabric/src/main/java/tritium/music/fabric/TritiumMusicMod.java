package tritium.music.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.client.AssetBootstrap;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.platform.MinecraftMusicPlatform;
import tritium.music.client.render.GuiStateReset;
import tritium.music.client.rendering.font.FontCatalog;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.SystemFontIndex;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public class TritiumMusicMod implements ClientModInitializer {

    private static String lastGlState;
    private static int glFrames;

    public static final String MOD_ID = "tritium-music";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String KEY_CATEGORY = KeyMapping.CATEGORY_MISC;

    public static final KeyMapping openNcmScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tritium-music.open", InputConstants.Type.KEYSYM, InputConstants.KEY_M, KEY_CATEGORY));

    private static final MusicInfoWidget MUSIC_INFO = new MusicInfoWidget();
    private static final MusicLyricsWidget MUSIC_LYRICS = new MusicLyricsWidget();
    private static final MusicSpectrumWidget MUSIC_SPECTRUM = new MusicSpectrumWidget();

    @Override
    public void onInitializeClient() {
        Platform.set(new MinecraftMusicPlatform());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            FontManager.loadFonts();
            FontCatalog.preload();
            SystemFontIndex.preload(FontManager::retryShaping);
            WidgetConfig.get();
            AssetBootstrap.start();
            AsyncUtil.runAsync(CloudMusic::initNCM);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CloudMusic.shutdownPlayback();
            CloudMusic.onStop();
            FontManager.dispose();
        });

        CloudMusic.addListener(new MusicListener() {
            @Override
            public void onLyricsLoaded(Music music) {
                AsyncUtil.runAsync(() -> {
                    synchronized (CloudMusic.lyrics) {
                        for (tritium.music.core.lyric.LyricLine line : CloudMusic.lyrics) {
                            FontManager.prewarmGlyphs(line.lyric);
                            if (line.translationText != null) {
                                FontManager.prewarmGlyphs(line.translationText);
                            }
                        }
                    }
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> {
            FrameProbe.beginFrame();
            FrameProbe.sample("world_start");
            GuiStateReset.auditFramebuffer("world_start");
            Minecraft client = Minecraft.getInstance();
            glFrames++;
            int[] box = new int[4];
            boolean scissor = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, box);
            int[] vp = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, vp);
            int[] cm = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, cm);
            double px = client.player == null ? 0 : client.player.getX();
            double py = client.player == null ? 0 : client.player.getY();
            double pz = client.player == null ? 0 : client.player.getZ();
            float yaw = client.player == null ? 0 : client.player.getYRot();
            float pitch = client.player == null ? 0 : client.player.getXRot();
            double cx = context.camera().getPosition().x;
            double cy = context.camera().getPosition().y;
            double cz = context.camera().getPosition().z;
            String state = "screen=" + (client.screen == null ? "-" : client.screen.getClass().getSimpleName())
                    + " paused=" + client.isPaused() + " hideGui=" + client.options.hideGui
                    + " scissor=" + scissor
                    + " vp=" + vp[0] + "," + vp[1] + "," + vp[2] + "," + vp[3]
                    + " fbo=" + org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
                    + " depth=" + org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                    + "/" + org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)
                    + " cmask=" + cm[0] + cm[1] + cm[2] + cm[3]
                    + " blend=" + org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_BLEND);
            String s = state + " box=" + box[0] + "," + box[1] + "," + box[2] + "," + box[3] + " fps=" + client.getFps()
                    + " pos=" + px + "," + py + "," + pz
                    + " rot=" + yaw + "," + pitch
                    + " cam=" + cx + "," + cy + "," + cz;
            boolean nan = Double.isNaN(px + py + pz + cx + cy + cz) || Float.isNaN(yaw + pitch);
            if (nan) {
                LOGGER.error("[glcheck] NAN {}", s);
            } else if (!state.equals(lastGlState)) {
                lastGlState = state;
                LOGGER.warn("[glcheck] {}", s);
            } else if (glFrames % 300 == 0) {
                LOGGER.warn("[glcheck] heartbeat frames={} gameTime={} dayTime={}", glFrames,
                        client.level == null ? -1 : client.level.getGameTime(),
                        client.level == null ? -1 : client.level.getDayTime());
            }
        });

    }

    public static void renderHudWidgets(GuiGraphics graphics, float partialTick) {
        updateSpectrumSettings();
        renderWidget(graphics, partialTick, MUSIC_SPECTRUM);
        renderWidget(graphics, partialTick, MUSIC_LYRICS);
        renderWidget(graphics, partialTick, MUSIC_INFO);
    }

    public static void auditHudState() {
        GuiStateReset.audit("hud_tail");
    }

    private static void updateSpectrumSettings() {
        AudioPlayer.spectrumEnabled = MUSIC_SPECTRUM.isEnabled() || MUSIC_LYRICS.isEnabled();
        WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;
        AudioPlayer.spectrumTilt = (float) spectrum.spectrumTilt;
        AudioPlayer.absoluteVolume = spectrum.absVol;
    }

    private static void renderWidget(GuiGraphics graphics, float partialTick, HudWidget widget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!widget.isEnabled() || minecraft.options.hideGui || minecraft.screen instanceof WidgetEditorScreen) {
            return;
        }

        HudWidget.renderInFrame(graphics, partialTick, widget::onRender);
    }

    private void onClientTick(Minecraft client) {
        if (client.screen != null) {
            return;
        }

        if (openNcmScreen.consumeClick()) {
            NCMScreen.open();
        }
    }
}
