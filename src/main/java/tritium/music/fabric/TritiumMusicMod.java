package tritium.music.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.MusicToastState;
import tritium.music.client.rendering.font.FontManager;
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

    private static final int OPEN_KEY = GLFW.GLFW_KEY_M;
    private boolean openKeyWasDown = false;

    private final MusicInfoWidget musicInfo = new MusicInfoWidget();
    private final MusicLyricsWidget musicLyrics = new MusicLyricsWidget();
    private final MusicSpectrumWidget musicSpectrum = new MusicSpectrumWidget();

    @Override
    public void onInitializeClient() {
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
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        registerWidget("music_info", musicInfo);
        registerWidget("music_lyrics", musicLyrics);
        registerWidget("music_spectrum", musicSpectrum);
    }

    private void registerWidget(String id, HudWidget widget) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, id), (graphics, deltaTracker) -> {
            AudioPlayer.spectrumEnabled = musicSpectrum.isEnabled() || musicLyrics.isEnabled();
            WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;
            AudioPlayer.spectrumTilt = (float) spectrum.spectrumTilt;
            AudioPlayer.absoluteVolume = spectrum.absVol;

            if (!widget.isEnabled() || Minecraft.getInstance().gui.screen() instanceof tritium.music.client.screens.WidgetEditorScreen) {
                return;
            }

            HudWidget.renderInFrame(graphics, deltaTracker.getGameTimeDeltaPartialTick(false), widget::onRender);
        });
    }

    private void onClientTick(Minecraft client) {
        if (client.gui.screen() != null) {
            openKeyWasDown = false;
            return;
        }

        boolean down = GLFW.glfwGetKey(client.getWindow().handle(), OPEN_KEY) == GLFW.GLFW_PRESS;
        if (down && !openKeyWasDown) {
            NCMScreen.open();
        }
        openKeyWasDown = down;
    }
}
