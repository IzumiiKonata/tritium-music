package tritium.music.client.rendering;

import tritium.music.client.config.WidgetConfig;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.core.CloudMusic;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.audio.MusicBeatGrid;
import tritium.music.core.audio.MusicBeatTracker;

public final class SongGroove {

    private double value;

    public double update(boolean enabled, double smoothing) {
        value = Interpolations.interpolate(value, enabled ? sample() : 0, smoothing);
        return value;
    }

    public double value() {
        return value;
    }

    public static boolean playing() {
        AudioPlayer player = CloudMusic.player;
        return player != null && !player.isPausing() && !player.isFinished() && WidgetConfig.get().songGroove;
    }

    public static double sample() {
        if (!playing()) {
            return 0;
        }
        MusicBeatGrid grid = MusicBeatTracker.currentGrid();
        return grid == null ? 0 : grid.pulseAt((long) CloudMusic.player.getCurrentTimeMillis(), WidgetConfig.get().groove.beatShift);
    }
}
