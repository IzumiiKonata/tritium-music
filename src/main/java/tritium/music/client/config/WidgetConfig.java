package tritium.music.client.config;

import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.core.MusicState;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WidgetConfig {

    private static WidgetConfig instance;

    public static WidgetConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public WidgetSettings musicInfo = new WidgetSettings(8f / 1920f, 8f / 1080f, 1.0, true);
    public WidgetSettings musicLyrics = new WidgetSettings(0.5f - 225f / 1920f, 1f - 140f / 1080f, 1.0, false);
    public WidgetSettings musicSpectrum = new WidgetSettings(0f, 0f, 1.0, false);

    public Lyrics lyrics = new Lyrics();
    public Spectrum spectrum = new Spectrum();

    public static class WidgetSettings {
        public double x;
        public double y;
        public double scale;
        public boolean enabled;

        public WidgetSettings() {
            this(0, 0, 1.0, false);
        }

        public WidgetSettings(double x, double y, double scale, boolean enabled) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.enabled = enabled;
        }
    }

    public static class Lyrics {
        public MusicLyricsWidget.ScrollEffects scrollEffect = MusicLyricsWidget.ScrollEffects.Scroll;
        public MusicLyricsWidget.AlignMode alignMode = MusicLyricsWidget.AlignMode.Center;
        public boolean shadow = false;
        public boolean singleLine = false;
        public boolean graceScroll = true;
        public boolean showTranslation = true;
        public boolean showRoman = false;
        public double lyricHeight = 20.0;
        public int width = 450;
        public int height = 120;
        public boolean auroraBloom = true;
        public boolean auroraSpark = true;
        public boolean audioReactive = true;
        public double auroraUnsungOpacity = 0.35;
        public int glowColor = new Color(140, 215, 255).getRGB();
    }

    public static class Spectrum {
        public MusicSpectrumWidget.Style style = MusicSpectrumWidget.Style.Rect;
        public boolean compatMode = false;
        public boolean indicator = true;
        public double multiplier = 1.0;
        public double smoothing = 0.55;
        public double spectrumTilt = 3.0;
        public boolean absVol = true;
        public double windowTime = 16.0;
        public boolean stereo = false;
        public int rectColor = new Color(125, 125, 125, 200).getRGB();
    }

    private static File file() {
        return new File(Platform.configDir(), "widgets.json");
    }

    public static WidgetConfig load() {
        File f = file();
        WidgetConfig config = null;

        if (f.exists()) {
            try {
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                config = JsonUtils.parse(json, WidgetConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (config == null) {
            config = new WidgetConfig();
        }

        config.normalize();
        config.applyToState();
        return config;
    }

    public void save() {
        applyToState();
        try {
            Files.writeString(file().toPath(), JsonUtils.toJsonString(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void normalize() {
        if (musicInfo == null) musicInfo = new WidgetSettings(8f / 1920f, 8f / 1080f, 1.0, true);
        if (musicLyrics == null) musicLyrics = new WidgetSettings(0.5f - 225f / 1920f, 1f - 140f / 1080f, 1.0, false);
        if (musicSpectrum == null) musicSpectrum = new WidgetSettings(0f, 0f, 1.0, false);
        if (lyrics == null) lyrics = new Lyrics();
        if (spectrum == null) spectrum = new Spectrum();
    }

    public void applyToState() {
        MusicState state = MusicState.get();
        state.setShowTranslation(lyrics.showTranslation);
        state.setShowRoman(lyrics.showRoman);
    }
}
