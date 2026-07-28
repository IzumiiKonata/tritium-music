package tritium.music.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.platform.MinecraftMusicPlatform;
import tritium.music.client.render.ClipPipeline;
import tritium.music.client.render.LinePipeline;
import tritium.music.client.render.RoundedPipeline;
import tritium.music.client.render.VerticalFadePipeline;
import tritium.music.client.rendering.MusicToastState;
import tritium.music.client.rendering.StencilCompositePipeline;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.rendering.shader.EffectPipelines;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.screens.ncm.panels.HudSettingsPanel;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

@Mod(value = TritiumMusicNeoForge.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TritiumMusicNeoForge.MOD_ID, value = Dist.CLIENT)
public final class TritiumMusicNeoForge {

    public static final String MOD_ID = "tritium_music";
    private static final String ASSET_NAMESPACE = "tritium-music";
    private static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ASSET_NAMESPACE, "ncm"));
    private static final KeyMapping OPEN_NCM_SCREEN = new KeyMapping(
            "key.tritium-music.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY);
    private static final MusicInfoWidget MUSIC_INFO = new MusicInfoWidget();
    private static final MusicLyricsWidget MUSIC_LYRICS = new MusicLyricsWidget();
    private static final MusicSpectrumWidget MUSIC_SPECTRUM = new MusicSpectrumWidget();

    public TritiumMusicNeoForge(ModContainer modContainer) {
        EffectPipelines.initialize();
        ClipPipeline.initialize();
        LinePipeline.initialize();
        RoundedPipeline.initialize();
        VerticalFadePipeline.initialize();
        StencilCompositePipeline.initialize();
        Platform.set(new MinecraftMusicPlatform());
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> NCMScreen.withPanel(new HudSettingsPanel(), parent));

        CloudMusic.addListener(new MusicListener() {
            @Override
            public void onSongStart(Music music) {
                MusicToastState.set(music.getArtistsName() + " - " + music.getName());
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> minecraft.gui.toastManager().showNowPlayingToast());
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
    }

    @SubscribeEvent
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(OPEN_NCM_SCREEN);
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        Identifier infoId = Identifier.fromNamespaceAndPath(ASSET_NAMESPACE, "music_info");
        Identifier lyricsId = Identifier.fromNamespaceAndPath(ASSET_NAMESPACE, "music_lyrics");
        Identifier spectrumId = Identifier.fromNamespaceAndPath(ASSET_NAMESPACE, "music_spectrum");
        registerWidgetBelow(event, VanillaGuiLayers.HOTBAR, infoId, MUSIC_INFO);
        registerWidgetAbove(event, infoId, lyricsId, MUSIC_LYRICS);
        registerWidgetAbove(event, lyricsId, spectrumId, MUSIC_SPECTRUM);
    }

    @SubscribeEvent
    private static void onClientStarted(ClientStartedEvent event) {
        FontManager.loadFonts();
        WidgetConfig.get();
        AsyncUtil.runAsync(CloudMusic::initNCM);
    }

    @SubscribeEvent
    private static void onClientStopping(ClientStoppingEvent event) {
        CloudMusic.shutdownPlayback();
        CloudMusic.onStop();
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == null && OPEN_NCM_SCREEN.consumeClick()) {
            NCMScreen.open();
        }
    }

    private static void registerWidgetBelow(
            RegisterGuiLayersEvent event,
            Identifier anchor,
            Identifier id,
            HudWidget widget) {
        event.registerBelow(
                anchor,
                id,
                (graphics, deltaTracker) -> {
                    updateSpectrumSettings();
                    if (!widget.isEnabled()
                            || Minecraft.getInstance().gui.screen() instanceof tritium.music.client.screens.WidgetEditorScreen) {
                        return;
                    }
                    HudWidget.renderInFrame(
                            graphics,
                            deltaTracker.getGameTimeDeltaPartialTick(false),
                            widget::onRender);
                });
    }

    private static void registerWidgetAbove(
            RegisterGuiLayersEvent event,
            Identifier anchor,
            Identifier id,
            HudWidget widget) {
        event.registerAbove(
                anchor,
                id,
                (graphics, deltaTracker) -> {
                    updateSpectrumSettings();
                    if (!widget.isEnabled()
                            || Minecraft.getInstance().gui.screen() instanceof tritium.music.client.screens.WidgetEditorScreen) {
                        return;
                    }
                    HudWidget.renderInFrame(
                            graphics,
                            deltaTracker.getGameTimeDeltaPartialTick(false),
                            widget::onRender);
                });
    }

    private static void updateSpectrumSettings() {
        AudioPlayer.spectrumEnabled = MUSIC_SPECTRUM.isEnabled() || MUSIC_LYRICS.isEnabled();
        WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;
        AudioPlayer.spectrumTilt = (float) spectrum.spectrumTilt;
        AudioPlayer.absoluteVolume = spectrum.absVol;
    }
}
