package tritium.music.core;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MusicState {

    private static final MusicState INSTANCE = new MusicState();

    public static MusicState get() {
        return INSTANCE;
    }

    private float volume = 0.25f;

    private volatile boolean downloading = false;
    private volatile double downloadProgress = 0;
    private volatile String downloadSpeed = "0 b/s";

    private boolean showTranslation = true;
    private boolean showRoman = false;
}
