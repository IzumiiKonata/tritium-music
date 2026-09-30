package tritium.music.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public class TritiumMusicMod implements ClientModInitializer {

    public static final String MOD_ID = "tritium-music";

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
    }

    public static void renderHudWidgets(GuiGraphics graphics, float partialTick) {
        updateSpectrumSettings();
        renderWidget(graphics, partialTick, MUSIC_SPECTRUM);
        renderWidget(graphics, partialTick, MUSIC_LYRICS);
        renderWidget(graphics, partialTick, MUSIC_INFO);
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
