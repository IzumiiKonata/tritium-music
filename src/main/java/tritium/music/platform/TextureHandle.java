package tritium.music.platform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

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
        return path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextureHandle that)) return false;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
