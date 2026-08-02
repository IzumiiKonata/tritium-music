package tritium.music.core.lyric.provider;

public record LyricsResult(String lyrics, String translation, String romanization, String format, String source) {
    public LyricsResult(String lyrics, String translation, String format, String source) {
        this(lyrics, translation, "", format, source);
    }

    public LyricsResult(String lyrics, String format, String source) {
        this(lyrics, "", "", format, source);
    }

    public boolean isEmpty() {
        return lyrics == null || lyrics.isBlank();
    }
}
