package tritium.music.platform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class TextureHandle {

    private final String namespace;
    private final String path;

    public static TextureHandle of(String path) {
        return new TextureHandle("tritium-music", normalize(path));
    }

    public static TextureHandle of(String namespace, String path) {
        return new TextureHandle(namespace, normalize(path));
    }

    private static String normalize(String path) {
        StringBuilder normalized = null;
        for (int i = 0; i < path.length(); i++) {
            char original = path.charAt(i);
            char value = original >= 'A' && original <= 'Z' ? (char) (original + ('a' - 'A')) : original;
            if (!isValidPathCharacter(value)) {
                value = '_';
            }
            if (normalized != null) {
                normalized.append(value);
            } else if (value != original) {
                normalized = new StringBuilder(path.length());
                normalized.append(path, 0, i).append(value);
            }
        }
        return normalized == null ? path : normalized.toString();
    }

    private static boolean isValidPathCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '/' || value == '.' || value == '_' || value == '-';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextureHandle that)) return false;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + path.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
