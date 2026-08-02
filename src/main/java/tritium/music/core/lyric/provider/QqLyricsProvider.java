package tritium.music.core.lyric.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QqLyricsProvider implements LyricsProvider {
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 Chrome/63.0.3239.132 Safari/537.36",
            "Referer", "https://c.y.qq.com/",
            "Cookie", "NMTID=" + UUID.randomUUID()
    );

    @Override
    public Optional<LyricsResult> search(LyricsQuery query) {
        try {
            String term = String.join(" ", query.artists(), query.title(), query.album()).trim();
            String search = ProviderHttp.get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&w="
                    + ProviderHttp.encodeQuery(term) + "&n=8", HEADERS);
            JsonArray songs = JsonParser.parseString(search).getAsJsonObject()
                    .getAsJsonObject("data").getAsJsonObject("song").getAsJsonArray("list");
            SelectedSong selected = bestSong(query, songs);
            if (selected == null) {
                return Optional.empty();
            }
            LyricsFetcher.log("QQ matched songId=" + selected.id() + ", songMid=" + selected.mid() + ", id=" + query.songId());
            Optional<LyricsResult> qrc = fetchQrc(selected.id());
            if (qrc.isPresent()) return qrc;
            if (selected.mid().isBlank()) return Optional.empty();
            String response = ProviderHttp.get("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                    + ProviderHttp.encodeQuery(selected.mid()) + "&format=json&nobase64=0", HEADERS);
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            String lyrics = decode(value(root, "lyric"));
            String translation = decode(root.has("trans") ? value(root, "trans") : value(root, "translate"));
            if (lyrics.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new LyricsResult(lyrics, translation, "lrc", "qq"));
        } catch (Exception e) {
            LyricsFetcher.log("QQ request failed for id=" + query.songId() + ", reason=" + e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<LyricsResult> fetchQrc(String songId) {
        if (songId.isBlank()) return Optional.empty();
        try {
            String response = ProviderHttp.postForm("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg", Map.of(
                    "version", "15",
                    "miniversion", "82",
                    "lrctype", "4",
                    "musicid", songId
            ), HEADERS);
            QqQrcCodec.Parts parts = QqQrcCodec.decodeResponse(response);
            if (parts.original().isBlank()) return Optional.empty();
            LyricsFetcher.log("QQ QRC decoded songId=" + songId + ", chars=" + parts.original().length()
                    + ", translationChars=" + parts.translation().length() + ", romanizationChars=" + parts.romanization().length());
            return Optional.of(new LyricsResult(parts.original(), parts.translation(), parts.romanization(), "qrc", "qq:qrc"));
        } catch (Exception e) {
            LyricsFetcher.log("QQ QRC request failed for songId=" + songId + ", reason=" + e.getMessage());
            return Optional.empty();
        }
    }

    private static SelectedSong bestSong(LyricsQuery query, JsonArray songs) {
        double bestScore = 0;
        SelectedSong bestSong = null;
        for (JsonElement element : songs) {
            JsonObject song = element.getAsJsonObject();
            String title = value(song, "songname");
            String artist = "";
            if (song.has("singer") && song.get("singer").isJsonArray() && !song.getAsJsonArray("singer").isEmpty()) {
                artist = value(song.getAsJsonArray("singer").get(0).getAsJsonObject(), "name");
            }
            String album = value(song, "albumname");
            double score = NeteaseLyricsProvider.score(query, artist, title, album);
            if (score > bestScore) {
                bestScore = score;
                String id = value(song, "songid");
                if (id.isBlank()) id = value(song, "id");
                bestSong = new SelectedSong(id, value(song, "songmid"));
            }
        }
        return bestSong;
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static String decode(String value) {
        if (value.isBlank() || !looksLikeBase64(value)) {
            return value;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static boolean looksLikeBase64(String value) {
        return value.length() >= 40 && value.matches("[A-Za-z0-9+/=\\r\\n]+");
    }

    private record SelectedSong(String id, String mid) {
    }
}
