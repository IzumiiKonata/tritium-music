package tritium.music.client.config;

import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.audio.AutoMixTempoPolicy;
import tritium.music.core.model.Quality;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.awt.*;
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

    public double volume = 0.25;
    public Quality quality = Quality.STANDARD;
    public boolean autoMix = false;
    public boolean autoMixTuneWheneverPossible = false;
    public PlaylistViewMode playlistViewMode = PlaylistViewMode.GRID;

    public enum PlaylistViewMode {
        LIST,
        GRID
    }

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

        public static final int MIN_LINES = 1;
        public static final int MAX_LINES = 7;
        public static final int LINE_STEP = 2;

        public MusicLyricsWidget.ScrollEffects scrollEffect = MusicLyricsWidget.ScrollEffects.Scroll;
        public MusicLyricsWidget.AlignMode alignMode = MusicLyricsWidget.AlignMode.Center;
        public boolean shadow = false;
        public boolean graceScroll = true;
        public boolean showTranslation = true;
        public boolean showRoman = false;
        public double lyricHeight = 20.0;
        public int lines = 3;
        public boolean auroraBloom = true;
        public boolean audioReactive = true;
        public double auroraUnsungOpacity = 0.35;
        public int glowColor = new Color(140, 215, 255).getRGB();

        public boolean singleLine() {
            return lines <= MIN_LINES;
        }

        public static int snapLines(int lines) {
            int snapped = MIN_LINES + Math.round((lines - MIN_LINES) / (float) LINE_STEP) * LINE_STEP;
            return Math.max(MIN_LINES, Math.min(MAX_LINES, snapped));
        }

        public void sanitize() {
            lines = snapLines(lines);
            if (singleLine() && alignMode == MusicLyricsWidget.AlignMode.Karaoke) {
                alignMode = MusicLyricsWidget.AlignMode.Center;
            }
        }
    }

    public static class Spectrum {
        public boolean indicator = true;
        public double multiplier = 1.0;
        public double smoothing = 0.55;
        public double spectrumTilt = 3.0;
        public boolean absVol = true;
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
        lyrics.sanitize();
        if (spectrum == null) spectrum = new Spectrum();
        if (quality == null) quality = Quality.STANDARD;
        if (playlistViewMode == null) playlistViewMode = PlaylistViewMode.GRID;
    }

    public void applyToState() {
        MusicState state = MusicState.get();
        state.setShowTranslation(lyrics.showTranslation);
        state.setShowRoman(lyrics.showRoman);
        state.setVolume((float) volume);
        CloudMusic.quality = quality;
        CloudMusic.autoMixEnabled = autoMix;
        AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE = autoMixTuneWheneverPossible ? 10.0 : 0.1;
    }
}
