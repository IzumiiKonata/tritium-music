package tritium.music.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TritiumShaders {

    public static final String NAMESPACE = "tritium-music";
    private static final Logger LOGGER = LoggerFactory.getLogger("tritium-music/shaders");
    private static final Map<String, ShaderInstance> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static @Nullable NamespaceRedirect provider;

    private TritiumShaders() {
    }

    public static @Nullable ShaderInstance get(String name, VertexFormat format) {
        ShaderInstance cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(name)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        ResourceProvider source = minecraft.getResourceManager();
        if (source == null) {
            return null;
        }
        if (provider == null || provider.delegate != source) {
            provider = new NamespaceRedirect(source);
        }

        try {
            ShaderInstance instance = new ShaderInstance(provider, name, format);
            CACHE.put(name, instance);
            return instance;
        } catch (Throwable throwable) {
            LOGGER.error("Unable to compile tritium shader {}", name, throwable);
            FAILED.add(name);
            return null;
        }
    }

    private static final class NamespaceRedirect implements ResourceProvider {

        private static final String PREFIX = "shaders/";
        private static final String DEFAULT_NAMESPACE = "minecraft";

        private final ResourceProvider delegate;

        private NamespaceRedirect(ResourceProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation location) {
            return this.delegate.getResource(redirect(location));
        }

        private static ResourceLocation redirect(ResourceLocation location) {
            if (!DEFAULT_NAMESPACE.equals(location.getNamespace())) {
                return location;
            }
            String path = location.getPath();
            if (!path.startsWith(PREFIX)) {
                return location;
            }
            return new ResourceLocation(NAMESPACE, path);
        }
    }
}
