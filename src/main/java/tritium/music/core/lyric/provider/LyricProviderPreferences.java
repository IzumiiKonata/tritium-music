package tritium.music.core.lyric.provider;

import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LyricProviderPreferences {
    private static LyricProviderPreferences instance;
    private Map<String, String> songs = new ConcurrentHashMap<>();

    public static synchronized LyricProviderPreferences get() {
        if (instance == null) instance = load();
        return instance;
    }

    public String provider(long songId) {
        return songs.getOrDefault(String.valueOf(songId), "");
    }

    public synchronized void select(long songId, String providerId) {
        songs.put(String.valueOf(songId), providerId);
        save();
    }

    private static LyricProviderPreferences load() {
        File file = file();
        if (file.exists()) {
            try {
                LyricProviderPreferences preferences = JsonUtils.parse(
                        Files.readString(file.toPath(), StandardCharsets.UTF_8),
                        LyricProviderPreferences.class);
                if (preferences != null && preferences.songs != null) {
                    preferences.songs = new ConcurrentHashMap<>(preferences.songs);
                    return preferences;
                }
            } catch (Exception e) {
                Platform.log("[Lyrics] Failed to load provider preferences: " + e.getMessage());
            }
        }
        return new LyricProviderPreferences();
    }

    private synchronized void save() {
        try {
            Files.writeString(file().toPath(), JsonUtils.toJsonString(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Platform.log("[Lyrics] Failed to save provider preferences: " + e.getMessage());
        }
    }

    private static File file() {
        return new File(Platform.configDir(), "lyric-providers.json");
    }
}
