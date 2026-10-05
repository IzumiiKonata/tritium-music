package tritium.music.core.audio;

import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.platform.Platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicBeatTracker {

    private static final int MAXIMUM_CACHED_GRIDS = 24;
    private static final long MODEL_WAIT_MILLIS = 30_000L;

    private static final Map<Long, MusicBeatGrid> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, MusicBeatGrid> eldest) {
                    return size() > MAXIMUM_CACHED_GRIDS;
                }
            });
    private static final Set<Long> REPORTED_REJECTIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean UNAVAILABLE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private static volatile boolean enabled;
    private static volatile long songId = -1L;
    private static volatile MusicBeatGrid grid;
    private static volatile Thread worker;
    private static volatile AudioPlayer workerPlayer;
    private static volatile String currentRejection;

    private MusicBeatTracker() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (value) {
            Music current = CloudMusic.currentlyPlaying;
            AudioPlayer player = CloudMusic.player;
            if (current != null && player != null) {
                onSongStarted(current, player);
            }
        } else {
            stop();
            songId = -1L;
            grid = null;
        }
    }

    public static void onSongStarted(Music song, AudioPlayer player) {
        stop();
        currentRejection = null;
        if (song == null || song.getDuration() < 30_000) {
            songId = -1L;
            grid = null;
            return;
        }
        songId = song.getId();
        grid = CACHE.get(song.getId());
        if (!enabled || grid != null || player == null) {
            return;
        }
        start(song, player);
    }

    public static MusicBeatGrid currentGrid() {
        return grid;
    }

    public static String currentRejection() {
        return grid == null ? currentRejection : null;
    }

    public static boolean analyzing() {
        Thread current = worker;
        return current != null && current.isAlive();
    }

    private static void start(Music song, AudioPlayer player) {
        long id = song.getId();
        Thread thread = new Thread(() -> run(id, player), "Music Beat Grid Analyzer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        worker = thread;
        workerPlayer = player;
        thread.start();
    }

    private static void stop() {
        Thread current = worker;
        worker = null;
        if (current != null) {
            current.interrupt();
        }
        AudioPlayer player = workerPlayer;
        workerPlayer = null;
        if (player != null) {
            player.cancelBeatGridAnalysis();
        }
    }

    private static void run(long id, AudioPlayer player) {
        try {
            if (!AutoMixSupport.prepare(MODEL_WAIT_MILLIS)) {
                if (UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
                    AutoMixSupport.State state = AutoMixSupport.state();
                    Platform.log("[NCM] Beat-synced cover effect unavailable: "
                            + (state.reasonKey() == null ? "unknown" : state.reasonKey())
                            + (state.detail() == null ? "" : " (" + state.detail() + ")"));
                }
                return;
            }
            player.analyzeBeatGrid(candidate -> accept(id, candidate));
        } catch (Throwable throwable) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                Platform.log("[NCM] Beat grid analysis failed: " + throwable);
            }
        }
    }

    private static void accept(long id, MusicBeatGrid candidate) {
        if (candidate == null || Thread.currentThread().isInterrupted() || songId != id) {
            return;
        }
        if (!candidate.isReliable()) {
            currentRejection = candidate.rejection();
            if (REPORTED_REJECTIONS.size() < MAXIMUM_CACHED_GRIDS * 2 && REPORTED_REJECTIONS.add(id)) {
                Platform.log("[NCM] Song groove disabled for song " + id + ": " + candidate.rejection());
            }
            return;
        }
        CACHE.put(id, candidate);
        currentRejection = null;
        grid = candidate;
    }
}
