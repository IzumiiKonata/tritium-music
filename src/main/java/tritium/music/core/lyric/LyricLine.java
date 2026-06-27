package tritium.music.core.lyric;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import tritium.music.core.util.Timer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author IzumiiKonata
 * Date: 2025/10/18 10:55
 */
public class LyricLine {

    @Getter
    public long timestamp;

    @Getter
    @Setter
    @NonNull
    public String lyric;

    public LyricLine(long timestamp, @NonNull String lyric) {
        this.timestamp = timestamp;
        this.lyric = lyric;
    }

    @Getter
    public String translationText;
    @Getter
    public String romanizationText;

    public long duration;
    public boolean shouldUpdatePosition = false;
    public Timer delayTimer = new Timer();
    public boolean isBreakLine = false;

    public double scrollWidth = 0;
    public double offsetX = 0;
    public double targetOffsetX = 0;
    public double offsetY = Double.MIN_VALUE;

    // Rendering state (engine-agnostic numeric values; written by the UI layer).
    public double posY = 0;
    public double height = 0;
    public float alpha = .4f;
    public float hoveringAlpha = 0f;
    public float blurAlpha = 0f;
    public double reboundAnimation = 0;
    public boolean renderEmphasizes = true;

    public float lineAlpha = .25f;
    public float auroraGlow = 0f;

    /**
     * Opaque per-line animation/layout state owned by the UI layer (e.g. a spring).
     * Kept as Object so the engine-agnostic core does not depend on the renderer.
     */
    public Object spring;

    public final List<Word> words = new CopyOnWriteArrayList<>();

    public static class Word {
        public final String word;
        public final long timestamp, duration;
        public final double[] emphasizes;

        public float alpha = 0.0f;
        public double progress = 0.0;

        public Word(String word, long timestamp, long duration) {
            this.word = word;
            this.timestamp = timestamp;
            this.duration = duration;
            this.emphasizes = new double[word.length()];
        }
    }

    private boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
