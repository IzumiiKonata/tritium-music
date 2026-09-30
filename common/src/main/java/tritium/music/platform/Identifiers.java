package tritium.music.platform;

import net.minecraft.resources.ResourceLocation;

public final class Identifiers {

    private Identifiers() {
    }

    public static ResourceLocation of(TextureHandle handle) {
        return new ResourceLocation(handle.namespace(), handle.path());
    }

    public static ResourceLocation of(String path) {
        return new ResourceLocation("tritium-music", path);
    }
}
