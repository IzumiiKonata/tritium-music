package tritium.music.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.core.CloudMusic;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public class TritiumMusicMod implements ClientModInitializer {

    public static final String MOD_ID = "tritium-music";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Platform.set(new FabricMusicPlatform());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> AsyncUtil.runAsync(CloudMusic::initNCM));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CloudMusic.onStop());
    }
}
