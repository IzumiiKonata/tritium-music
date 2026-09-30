package tritium.music.client.rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.jspecify.annotations.Nullable;
import tritium.music.client.config.WidgetConfig;

/**
 * Bridges NCM playback to the vanilla now-playing toast. The mixins on
 * {@code NowPlayingToast} / {@code ToastManager} read {@link #text} so the
 * vanilla toast displays our song string and renders even when the vanilla
 * music-toast option is disabled.
 */
public final class MusicToastState {

    @Nullable
    private static volatile String text = null;
    private static volatile boolean owned = false;
    private static boolean claiming = false;

    private MusicToastState() {
    }

    @Nullable
    public static String text() {
        return text;
    }

    public static boolean active() {
        return owned && text != null && mode() != WidgetConfig.MusicToastMode.OFF;
    }

    public static void nowPlayingToastShown() {
        owned = claiming;
        claiming = false;
    }

    public static void push(String value) {
        WidgetConfig.MusicToastMode mode = mode();
        if (mode == WidgetConfig.MusicToastMode.OFF) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            MusicToastDisplayState gameState = minecraft.options.musicToast().get();
            if (mode == WidgetConfig.MusicToastMode.FOLLOW_GAME && !gameState.renderInPauseScreen()) {
                return;
            }

            text = value;
            owned = true;

            if (mode == WidgetConfig.MusicToastMode.FOLLOW_GAME && !gameState.renderToast()) {
                return;
            }

            ToastManager toastManager = minecraft.getToastManager();
            if (!gameState.renderToast()) {
                toastManager.setMusicToastDisplayState(MusicToastDisplayState.PAUSE);
            }
            claiming = true;
            try {
                toastManager.showNowPlayingToast();
            } finally {
                claiming = false;
            }
        });
    }

    private static WidgetConfig.MusicToastMode mode() {
        return WidgetConfig.get().musicToastMode;
    }
}
