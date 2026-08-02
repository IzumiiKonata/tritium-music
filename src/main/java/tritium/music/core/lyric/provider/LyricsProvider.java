package tritium.music.core.lyric.provider;

import java.util.Optional;

public interface LyricsProvider {
    Optional<LyricsResult> search(LyricsQuery query);
}
