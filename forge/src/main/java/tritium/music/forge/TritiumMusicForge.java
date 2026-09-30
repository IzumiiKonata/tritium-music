package tritium.music.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import tritium.music.client.AssetBootstrap;
import tritium.music.client.config.WidgetConfig;
import tritium.music.client.platform.MinecraftMusicPlatform;
import tritium.music.client.rendering.font.FontCatalog;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.SystemFontIndex;
import tritium.music.client.rendering.hud.HudWidget;
import tritium.music.client.rendering.hud.MusicInfoWidget;
import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.screens.WidgetEditorScreen;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.client.screens.ncm.panels.HudSettingsPanel;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

@Mod(TritiumMusicForge.MOD_ID)
@Mod.EventBusSubscriber(modid = TritiumMusicForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TritiumMusicForge {

    public static final String MOD_ID = "tritium_music";
    public static final String KEY_CATEGORY = "key.category.tritium-music.ncm";

    public static final KeyMapping OPEN_NCM_SCREEN = new KeyMapping("key.tritium-music.open", InputConstants.Type.KEYSYM, InputConstants.KEY_M, KEY_CATEGORY);
    private static final MusicInfoWidget MUSIC_INFO = new MusicInfoWidget();
    private static final MusicLyricsWidget MUSIC_LYRICS = new MusicLyricsWidget();
    private static final MusicSpectrumWidget MUSIC_SPECTRUM = new MusicSpectrumWidget();

    public TritiumMusicForge() {
        Platform.set(new MinecraftMusicPlatform());
        MinecraftForge.EVENT_BUS.register(TritiumMusicForge.class);
        MinecraftForge.EVENT_BUS.register(TMForgeEventsListener.class);

        ModList.get().getModContainerById(MOD_ID).ifPresent(container -> container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> NCMScreen.withPanel(new HudSettingsPanel(), parent))));

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
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_NCM_SCREEN);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "music_spectrum", (gui, graphics, partialTick, width, height) -> renderWidget(graphics, partialTick, MUSIC_SPECTRUM));
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "music_lyrics", (gui, graphics, partialTick, width, height) -> renderWidget(graphics, partialTick, MUSIC_LYRICS));
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "music_info", (gui, graphics, partialTick, width, height) -> renderWidget(graphics, partialTick, MUSIC_INFO));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TritiumMusicForge::onClientStarted);
    }

    private static void onClientStarted() {
        FontManager.loadFonts();
        FontCatalog.preload();
        SystemFontIndex.preload(FontManager::retryShaping);
        WidgetConfig.get();
        AssetBootstrap.start();
        AsyncUtil.runAsync(CloudMusic::initNCM);
    }

    private static void renderWidget(GuiGraphics graphics, float partialTick, HudWidget widget) {
        updateSpectrumSettings();

        Minecraft minecraft = Minecraft.getInstance();
        if (!widget.isEnabled() || minecraft.options.hideGui || minecraft.screen instanceof WidgetEditorScreen) {
            return;
        }

        HudWidget.renderInFrame(graphics, partialTick, widget::onRender);
    }

    private static void updateSpectrumSettings() {
        AudioPlayer.spectrumEnabled = MUSIC_SPECTRUM.isEnabled() || MUSIC_LYRICS.isEnabled();
        WidgetConfig.Spectrum spectrum = WidgetConfig.get().spectrum;
        AudioPlayer.spectrumTilt = (float) spectrum.spectrumTilt;
        AudioPlayer.absoluteVolume = spectrum.absVol;
    }
}
