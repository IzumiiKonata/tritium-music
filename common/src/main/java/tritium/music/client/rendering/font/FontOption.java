package tritium.music.client.rendering.font;

import java.util.Locale;

public record FontOption(String source, String value, String displayName, String detailKey) {

    public String id() {
        return source + ":" + value;
    }

    public boolean matches(String query) {
        if (query == null) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return displayName.toLowerCase(Locale.ROOT).contains(needle)
                || value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
