package tritium.music.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.MusicToastState;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.shader.EffectPipelines;
import tritium.music.client.render.RoundedPipeline;
import tritium.music.client.render.ClipPipeline;
import tritium.music.client.render.VerticalFadePipeline;
import tritium.music.client.rendering.StencilCompositePipeline;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public class TritiumMusicMod implements ClientModInitializer {

    public static final String MOD_ID = "tritium-music";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "ncm"));

    public static final KeyMapping openNcmScreen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tritium-music.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY));

    private final MusicInfoWidget musicInfo = new MusicInfoWidget();
    private final MusicLyricsWidget musicLyrics = new MusicLyricsWidget();
    private final MusicSpectrumWidget musicSpectrum = new MusicSpectrumWidget();

    @Override
    public void onInitializeClient() {
        EffectPipelines.initialize();
        ClipPipeline.initialize();
        RoundedPipeline.initialize();
        VerticalFadePipeline.initialize();
        StencilCompositePipeline.initialize();
        Platform.set(new FabricMusicPlatform());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            FontManager.loadFonts();
            WidgetConfig.get();
            AsyncUtil.runAsync(CloudMusic::initNCM);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CloudMusic.shutdownPlayback();
            CloudMusic.onStop();
        });

        CloudMusic.addListener(new MusicListener() {
            @Override
            public void onSongStart(Music music) {
                MusicToastState.set(music.getArtistsName() + " - " + music.getName());
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.toastManager().showNowPlayingToast());
            }

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

        registerWidget("music_info", musicInfo);
        registerWidget("music_lyrics", musicLyrics);
        registerWidget("music_spectrum", musicSpectrum);
    }

    private void registerWidget(String id, HudWidget widget) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(MOD_ID, id), (graphics, deltaTracker) -> {
            AudioPlayer.spectrumEnabled = musicSpectrum.isEnabled() || musicLyrics.isEnabled();
            WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;
            AudioPlayer.spectrumTilt = (float) spectrum.spectrumTilt;
            AudioPlayer.absoluteVolume = spectrum.absVol;
            AudioPlayer.windowTime = (float) spectrum.windowTime;
            AudioPlayer.stereo = spectrum.stereo;

            if (!widget.isEnabled() || Minecraft.getInstance().gui.screen() instanceof tritium.music.client.screens.WidgetEditorScreen) {
                return;
            }

            HudWidget.renderInFrame(graphics, deltaTracker.getGameTimeDeltaPartialTick(false), widget::onRender);
        });
    }

    private void onClientTick(Minecraft client) {
        if (client.gui.screen() != null) {
            return;
        }

        if (openNcmScreen.consumeClick()) {
            NCMScreen.open();
        }
    }
}
