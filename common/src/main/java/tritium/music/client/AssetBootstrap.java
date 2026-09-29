package tritium.music.client;

import tritium.music.client.rendering.font.FontCatalog;
import tritium.music.client.rendering.font.FontLibrary;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.SystemFontIndex;
import tritium.music.core.assets.AssetManager;
import tritium.music.core.util.AsyncUtil;

public final class AssetBootstrap {

    private static boolean started;
    private static boolean fontsPending;
    private static boolean fontsReloaded;

    private AssetBootstrap() {
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;

        AssetManager manager = AssetManager.get();
        AssetManager.Snapshot snapshot = manager.snapshot();
        fontsPending = !snapshot.essentialReady();
        manager.addListener(AssetBootstrap::onSnapshot);
        if (!snapshot.complete()) {
            manager.start();
        }
    }

    private static void onSnapshot(AssetManager.Snapshot snapshot) {
        if (!fontsPending || !snapshot.essentialReady() || fontsReloaded) {
            return;
        }
        fontsReloaded = true;
        fontsPending = false;
        AsyncUtil.runOnRenderThread(() -> {
            FontLibrary.invalidateUnavailable();
            FontCatalog.refresh();
            SystemFontIndex.preload(FontManager::retryShaping);
            FontManager.reload();
        });
    }
}
