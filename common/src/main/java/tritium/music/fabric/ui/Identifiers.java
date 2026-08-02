package tritium.music.fabric.ui;

import net.minecraft.resources.Identifier;
import tritium.music.platform.TextureHandle;

public final class Identifiers {

    private Identifiers() {
    }

    public static Identifier of(TextureHandle handle) {
        return Identifier.fromNamespaceAndPath(handle.getNamespace(), handle.getPath());
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath("tritium-music", path);
    }
}
