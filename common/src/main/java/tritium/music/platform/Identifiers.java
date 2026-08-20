package tritium.music.platform;

import net.minecraft.resources.Identifier;

public final class Identifiers {

    private Identifiers() {
    }

    public static Identifier of(TextureHandle handle) {
        return Identifier.fromNamespaceAndPath(handle.namespace(), handle.path());
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }
}
