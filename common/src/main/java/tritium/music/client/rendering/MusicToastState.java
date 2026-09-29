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

    private MusicToastState() {
    }

    @Nullable
    public static String text() {
        return text;
    }

    public static boolean active() {
        return text != null && mode() != WidgetConfig.MusicToastMode.OFF;
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

            if (mode == WidgetConfig.MusicToastMode.FOLLOW_GAME && !gameState.renderToast()) {
                return;
            }

            ToastManager toastManager = minecraft.gui.toastManager();
            if (!gameState.renderToast()) {
                toastManager.setMusicToastDisplayState(MusicToastDisplayState.PAUSE);
            }
            toastManager.showNowPlayingToast();
        });
    }

    private static WidgetConfig.MusicToastMode mode() {
        return WidgetConfig.get().musicToastMode;
    }
}
