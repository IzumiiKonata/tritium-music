package tritium.music.core.lyric.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tritium.music.core.ncm.api.CloudMusicApi;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class NeteaseLyricsProvider implements LyricsProvider {
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 AppleWebKit/537.36 Chrome/119.0.0.0 Safari/537.36",
            "Referer", "https://music.163.com/",
            "Accept", "application/json, text/plain, */*"
    );

    @Override
    public Optional<LyricsResult> search(LyricsQuery query) {
        try {
            long songId = query.songId() > 0 ? query.songId() : searchSongId(query);
            if (songId <= 0) {
                return Optional.empty();
            }
            String json = query.songId() > 0
                    ? CloudMusicApi.lyricNew(songId).toString()
                    : ProviderHttp.get("https://music.163.com/api/song/lyric?os=pc&id=" + songId + "&lv=-1&kv=-1&tv=-1", HEADERS);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!hasLyrics(root)) {
                return Optional.empty();
            }
            return Optional.of(new LyricsResult(json, "netease", "netease"));
        } catch (Exception e) {
            LyricsFetcher.log("NetEase request failed for id=" + query.songId() + ", reason=" + e.getMessage());
            return Optional.empty();
        }
    }

    private static long searchSongId(LyricsQuery query) throws Exception {
        String term = String.join(" ", query.artists(), query.title(), query.album()).trim();
        String body = ProviderHttp.get("https://music.163.com/api/search/get/?s=" + ProviderHttp.encodeQuery(term) + "&type=1&limit=8", HEADERS);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("result") || !root.getAsJsonObject("result").has("songs")) {
            return 0;
        }
        JsonArray songs = root.getAsJsonObject("result").getAsJsonArray("songs");
        double bestScore = 0;
        long bestId = 0;
        for (JsonElement element : songs) {
            JsonObject song = element.getAsJsonObject();
            String title = string(song, "name");
            String artist = firstArtist(song);
            String album = album(song);
            double score = score(query, artist, title, album);
            if (score > bestScore) {
                bestScore = score;
                bestId = song.has("id") ? song.get("id").getAsLong() : 0;
            }
        }
        return bestId;
    }

    private static boolean hasLyrics(JsonObject root) {
        return hasText(root, "lrc") || hasText(root, "yrc");
    }

    private static boolean hasText(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject()
                && root.getAsJsonObject(key).has("lyric")
                && !root.getAsJsonObject(key).get("lyric").getAsString().isBlank();
    }

    private static String firstArtist(JsonObject song) {
        String key = song.has("artists") ? "artists" : "ar";
        if (!song.has(key) || !song.get(key).isJsonArray() || song.getAsJsonArray(key).isEmpty()) {
            return "";
        }
        return string(song.getAsJsonArray(key).get(0).getAsJsonObject(), "name");
    }

    private static String album(JsonObject song) {
        String key = song.has("album") ? "album" : "al";
        return song.has(key) && song.get(key).isJsonObject() ? string(song.getAsJsonObject(key), "name") : "";
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    static double score(LyricsQuery query, String artist, String title, String album) {
        String expectedTitle = normalize(query.title());
        String expectedArtist = normalize(query.artists());
        String expectedAlbum = normalize(query.album());
        String actualTitle = normalize(title);
        String actualArtist = normalize(artist);
        String actualAlbum = normalize(album);
        double score = containsEither(actualTitle, expectedTitle) ? 5 : 0;
        score += containsEither(actualArtist, expectedArtist) ? 3 : 0;
        score += containsEither(actualAlbum, expectedAlbum) ? 2 : 0;
        if (!expectedTitle.isEmpty() && actualTitle.equals(expectedTitle)) score += 2;
        if (!expectedArtist.isEmpty() && actualArtist.equals(expectedArtist)) score += 1;
        return score;
    }

    private static boolean containsEither(String left, String right) {
        return !left.isEmpty() && !right.isEmpty() && (left.contains(right) || right.contains(left));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
