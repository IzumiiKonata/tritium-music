package tritium.music.client.config;

import tritium.music.client.rendering.hud.MusicLyricsWidget;
import tritium.music.client.rendering.hud.MusicSpectrumWidget;
import tritium.music.client.util.ClientSettings;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicState;
import tritium.music.core.audio.AutoMixTempoPolicy;
import tritium.music.core.audio.MusicBeatTracker;
import tritium.music.core.model.Quality;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WidgetConfig {

    private static final int SPECTRUM_PLACEMENT_VERSION = 2;
    private static final double SPECTRUM_ANCHOR_Y = 1 - MusicSpectrumWidget.HEIGHT_RATIO;

    private static volatile WidgetConfig instance;

    public static WidgetConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static boolean spectrumRequested() {
        WidgetConfig config = get();
        return config.musicSpectrum.enabled || (config.musicLyrics.enabled && config.lyrics.audioReactive);
    }

    public WidgetSettings musicInfo = new WidgetSettings(8f / 1920f, 8f / 1080f, 1.0, true);
    public WidgetSettings musicLyrics = new WidgetSettings(0.5f - 225f / 1920f, 1f - 140f / 1080f, 1.0, false);
    public WidgetSettings musicSpectrum = new WidgetSettings(0, SPECTRUM_ANCHOR_Y, 1.0, false);
    public WidgetSettings musicDance = new WidgetSettings(0.5f - 102f / 1920f, 1f - 250f / 1080f, 1.0, false);

    public Lyrics lyrics = new Lyrics();
    public Spectrum spectrum = new Spectrum();
    public Dance dance = new Dance();

    public double volume = 0.25;
    public Quality quality = Quality.STANDARD;
    public boolean autoMix = false;
    public boolean autoMixTuneWheneverPossible = false;
    public boolean songGroove = false;
    public Groove groove = new Groove();
    public boolean showWidgetBoundary = false;
    public boolean debugMode = false;
    public boolean grooveInfo = false;
    public boolean grooveMarkers = false;
    public PlaylistViewMode playlistViewMode = PlaylistViewMode.GRID;
    public CloudMusic.PlayMode playMode = CloudMusic.PlayMode.Sequential;
    public int configVersion = 0;

    public enum PlaylistViewMode {
        LIST,
        GRID
    }

    public static class Groove {

        public static final double MIN_STRENGTH = 0.005;
        public static final double MAX_STRENGTH = 0.08;
        public static final double DEFAULT_STRENGTH = 0.03;
        public static final double DEFAULT_GLOW_STRENGTH = 0.08;
        public static final double DEFAULT_LIFT_STRENGTH = 0.06;
        public static final double DEFAULT_AURORA_STRENGTH = 0.5;
        public static final double DEFAULT_DOT_STRENGTH = 0.35;
        public static final int MIN_BEAT_SHIFT = -8;
        public static final int MAX_BEAT_SHIFT = 8;

        public boolean cover = true;
        public double coverStrength = DEFAULT_STRENGTH;
        public boolean coverGlow = true;
        public double coverGlowStrength = DEFAULT_GLOW_STRENGTH;
        public boolean lyricLift = true;
        public double lyricLiftStrength = DEFAULT_LIFT_STRENGTH;
        public boolean auroraPulse = true;
        public double auroraPulseStrength = DEFAULT_AURORA_STRENGTH;
        public boolean breakDots = true;
        public double breakDotsStrength = DEFAULT_DOT_STRENGTH;
        public int beatShift = 0;

        public boolean anyEnabled() {
            return cover || coverGlow || lyricLift || auroraPulse || breakDots;
        }

        public void sanitize() {
            coverStrength = strength(coverStrength, DEFAULT_STRENGTH);
            coverGlowStrength = strength(coverGlowStrength, DEFAULT_GLOW_STRENGTH);
            lyricLiftStrength = strength(lyricLiftStrength, DEFAULT_LIFT_STRENGTH);
            auroraPulseStrength = strength(auroraPulseStrength, DEFAULT_AURORA_STRENGTH);
            breakDotsStrength = strength(breakDotsStrength, DEFAULT_DOT_STRENGTH);
            beatShift = Math.max(MIN_BEAT_SHIFT, Math.min(MAX_BEAT_SHIFT, beatShift));
        }

        private static double strength(double value, double fallback) {
            if (!Double.isFinite(value)) {
                return fallback;
            }
            return Math.max(0, Math.min(1, value));
        }
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

    public static class Dance {

        public static final double MIN_SPEED = 0.25;
        public static final double MAX_SPEED = 3.0;
        public static final double MAX_PULSE = 0.3;

        public String style = "";
        public boolean beatSync = true;
        public double speed = 1.0;
        public double frameDurationMs = 0;
        public double beatPulse = 0.12;
        public boolean mirror = false;
        public double opacity = 1.0;

        public void sanitize() {
            if (style == null) {
                style = "";
            }
            speed = limit(speed, 1.0, MIN_SPEED, MAX_SPEED);
            frameDurationMs = limit(frameDurationMs, 0, 0, 2000);
            beatPulse = limit(beatPulse, 0.12, 0, MAX_PULSE);
            opacity = limit(opacity, 1.0, 0.05, 1.0);
        }

        private static double limit(double value, double fallback, double min, double max) {
            if (!Double.isFinite(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }
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
        if (musicSpectrum == null) musicSpectrum = new WidgetSettings(0, SPECTRUM_ANCHOR_Y, 1.0, false);
        if (musicDance == null) musicDance = new WidgetSettings(0.5f - 102f / 1920f, 1f - 250f / 1080f, 1.0, false);
        if (configVersion < SPECTRUM_PLACEMENT_VERSION) {
            configVersion = SPECTRUM_PLACEMENT_VERSION;
            musicSpectrum.x = 0;
            musicSpectrum.y = SPECTRUM_ANCHOR_Y;
        }
        if (lyrics == null) lyrics = new Lyrics();
        lyrics.sanitize();
        if (spectrum == null) spectrum = new Spectrum();
        if (dance == null) dance = new Dance();
        dance.sanitize();
        if (quality == null) quality = Quality.STANDARD;
        if (playlistViewMode == null) playlistViewMode = PlaylistViewMode.GRID;
        if (playMode == null) playMode = CloudMusic.PlayMode.Sequential;
        if (groove == null) groove = new Groove();
        groove.sanitize();
    }

    public void applyToState() {
        MusicState state = MusicState.get();
        state.setShowTranslation(lyrics.showTranslation);
        state.setShowRoman(lyrics.showRoman);
        state.setVolume((float) volume);
        CloudMusic.quality = quality;
        CloudMusic.playMode = playMode;
        CloudMusic.autoMixEnabled = autoMix;
        AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE = autoMixTuneWheneverPossible ? 10.0 : 0.1;
        MusicBeatTracker.setEnabled(songGroove && groove.anyEnabled());
        ClientSettings.SHOW_WIDGET_BOUNDARY.setValue(showWidgetBoundary);
        ClientSettings.DEBUG_MODE.setValue(debugMode);
    }
}
