package tritium.music.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.MusicToast;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.MusicListener;
import tritium.music.core.model.Music;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public class TritiumMusicMod implements ClientModInitializer {

    public static final String MOD_ID = "tritium-music";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int OPEN_KEY = GLFW.GLFW_KEY_M;
    private boolean openKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        Platform.set(new FabricMusicPlatform());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            FontManager.loadFonts();
            AsyncUtil.runAsync(CloudMusic::initNCM);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CloudMusic.onStop());

        CloudMusic.addListener(new MusicListener() {
            @Override
            public void onSongStart(Music music) {
                MusicToast.pushMusicToast(music.getArtistsName() + " - " + music.getName());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "music_toast"), (graphics, deltaTracker) -> {
            RenderContext.begin(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
            Interpolations.calcFrameDelta();
            try {
                MusicToast.render();
            } finally {
                RenderContext.end();
            }
        });
    }

    private void onClientTick(Minecraft client) {
        if (client.gui.screen() != null) {
            openKeyWasDown = false;
            return;
        }

        boolean down = GLFW.glfwGetKey(client.getWindow().handle(), OPEN_KEY) == GLFW.GLFW_PRESS;
        if (down && !openKeyWasDown) {
            NCMScreen.open();
        }
        openKeyWasDown = down;
    }
}
