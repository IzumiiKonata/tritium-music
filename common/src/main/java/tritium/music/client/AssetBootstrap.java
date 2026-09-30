package tritium.music.client;

import tritium.music.client.rendering.font.FontCatalog;
import tritium.music.client.rendering.font.FontLibrary;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.font.SystemFontIndex;
import tritium.music.core.assets.AssetManager;
import tritium.music.core.audio.AutoMixSupport;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

public final class AssetBootstrap {

    private static boolean started;
    private static boolean fontsPending;
    private static boolean fontsReloaded;
    private static boolean fontsFailureNotified;
    private static boolean autoMixFailureNotified;

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
        AutoMixSupport.addListener(AssetBootstrap::onAutoMixState);
        if (!snapshot.complete()) {
            manager.start();
        }
    }

    private static void onSnapshot(AssetManager.Snapshot snapshot) {
        if (snapshot.unavailable() && !snapshot.essentialReady() && !fontsFailureNotified) {
            fontsFailureNotified = true;
            notifyDisabled("tritium-music.ui.feature.fonts.disabled",
                    "tritium-music.ui.feature.reason.download", snapshot.failure());
        }

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

    private static void onAutoMixState(AutoMixSupport.State state) {
        if (state.available() || !state.terminal() || autoMixFailureNotified) {
            return;
        }
        autoMixFailureNotified = true;
        notifyDisabled("tritium-music.ui.feature.automix.disabled", state.reasonKey(), state.detail());
    }

    private static void notifyDisabled(String titleKey, String reasonKey, String detail) {
        String title = Platform.translate(titleKey);
        String reason = reasonKey == null
                ? ""
                : Platform.translate(reasonKey, detail == null || detail.isBlank() ? "-" : detail);
        Platform.log("[asset] " + title + " " + reason);
        AsyncUtil.runOnRenderThread(() -> Platform.sendChatMessage("§6" + title + " §7" + reason));
    }
}
