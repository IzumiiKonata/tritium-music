package tritium.music.client.rendering;

import org.jspecify.annotations.Nullable;

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
        return text != null;
    }

    public static void set(@Nullable String value) {
        text = value;
    }
}
