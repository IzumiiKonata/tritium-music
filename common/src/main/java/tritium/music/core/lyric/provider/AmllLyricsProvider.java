package tritium.music.core.lyric.provider;

import java.util.*;

public final class AmllLyricsProvider implements LyricsProvider {
    private static final String BASE_URL = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/";

    @Override
    public String id() {
        return "amll";
    }

    @Override
    public String displayName() {
        return "AMLL";
    }

    @Override
    public Optional<LyricsResult> search(LyricsQuery query) {
        if (query.songId() > 0) {
            Optional<LyricsResult> byId = fetch(BASE_URL + "ncm-lyrics/" + query.songId() + ".ttml", query);
            if (byId.isPresent()) return byId;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (!query.artists().isBlank() && !query.title().isBlank()) {
            names.add(query.artists() + " - " + query.title() + ".ttml");
        }
        if (!query.title().isBlank()) {
            names.add(query.title() + ".ttml");
            names.add(query.title().replace(' ', '_') + ".ttml");
        }
        List<String> candidates = new ArrayList<>(names);
        for (String candidate : candidates) {
            Optional<LyricsResult> result = fetch(BASE_URL + ProviderHttp.encodePath(candidate), query);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static Optional<LyricsResult> fetch(String url, LyricsQuery query) {
        try {
            String body = ProviderHttp.get(url, Map.of("User-Agent", "TritiumMusic/1.0"));
            if (body.contains("<tt")) return Optional.of(new LyricsResult(body, "ttml", "amll"));
            LyricsFetcher.log("AMLL response is not TTML for id=" + query.songId() + ", url=" + url);
        } catch (Exception e) {
            LyricsFetcher.log("AMLL miss for id=" + query.songId() + ", url=" + url + ", reason=" + e.getMessage());
        }
        return Optional.empty();
    }
}
