package tritium.music.core.lyric.provider;

import java.util.Optional;

public interface LyricsProvider {
    String id();

    String displayName();

    Optional<LyricsResult> search(LyricsQuery query);
}
