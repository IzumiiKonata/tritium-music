package tritium.music.core.audio;

import tritium.music.core.CloudMusic;
import tritium.music.core.model.Music;
import tritium.music.platform.Platform;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicBeatTracker {

    public static final String SOURCE_CACHE = "cache";
    public static final String SOURCE_LIVE = "live";
    public static final String SOURCE_PREFETCH = "prefetch";

    private static final int MAXIMUM_CACHED_GRIDS = 24;
    private static final int MAXIMUM_CACHED_REJECTIONS = 64;
    private static final int MAXIMUM_PREFETCH_QUEUE = 4;
    private static final long MINIMUM_SONG_MILLIS = 30_000L;
    private static final long MODEL_WAIT_MILLIS = 30_000L;
    private static final long PREFETCH_LIVE_WAIT_MILLIS = 180_000L;

    private static final Map<Long, MusicBeatGrid> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, MusicBeatGrid> eldest) {
                    return size() > MAXIMUM_CACHED_GRIDS;
                }
            });
    private static final Map<Long, String> REJECTIONS = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                    return size() > MAXIMUM_CACHED_REJECTIONS;
                }
            });
    private static final Set<Long> REPORTED_REJECTIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean UNAVAILABLE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean PREFETCH_FAILURE_LOGGED = new AtomicBoolean();

    private static final Object PREFETCH_LOCK = new Object();
    private static final Deque<Music> PREFETCH_QUEUE = new ArrayDeque<>();
    private static final Set<Long> PREFETCH_PENDING = new HashSet<>();

    private static volatile boolean enabled;
    private static volatile long songId = -1L;
    private static volatile MusicBeatGrid grid;
    private static volatile String gridSource;
    private static volatile Thread worker;
    private static volatile AudioPlayer workerPlayer;
    private static volatile String currentRejection;
    private static volatile Thread prefetchWorker;
    private static volatile AudioPlayer prefetchPlayer;
    private static volatile long prefetchSongId = -1L;
    private static volatile int prefetchGeneration;

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
            stopPrefetch();
            songId = -1L;
            grid = null;
            gridSource = null;
        }
    }

    public static void onSongStarted(Music song, AudioPlayer player) {
        stop();
        currentRejection = null;
        if (song == null || song.getDuration() < MINIMUM_SONG_MILLIS) {
            songId = -1L;
            grid = null;
            gridSource = null;
            return;
        }
        songId = song.getId();
        restore(songId);
        MusicBeatGrid cached = CACHE.get(songId);
        grid = cached;
        gridSource = cached == null ? null : SOURCE_CACHE;
        String rejection = REJECTIONS.get(songId);
        currentRejection = rejection;
        if (!enabled || player == null || rejection != null || (cached != null && cached.isComplete())) {
            return;
        }
        if (prefetching(songId)) {
            return;
        }
        start(song, player);
    }

    public static void prefetch(Music song) {
        if (!enabled || song == null || song.getDuration() < MINIMUM_SONG_MILLIS) {
            return;
        }
        long id = song.getId();
        if (id == songId) {
            return;
        }
        restore(id);
        if (CACHE.containsKey(id) || REJECTIONS.containsKey(id)) {
            return;
        }
        synchronized (PREFETCH_LOCK) {
            if (!PREFETCH_PENDING.add(id)) {
                return;
            }
            while (PREFETCH_QUEUE.size() >= MAXIMUM_PREFETCH_QUEUE) {
                Music dropped = PREFETCH_QUEUE.pollLast();
                PREFETCH_PENDING.remove(dropped.getId());
            }
            PREFETCH_QUEUE.addLast(song);
            if (prefetchWorker == null || !prefetchWorker.isAlive()) {
                Thread thread = new Thread(MusicBeatTracker::prefetchLoop, "Music Beat Grid Prefetch");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                prefetchWorker = thread;
                thread.start();
            }
        }
    }

    public static MusicBeatGrid currentGrid() {
        return grid;
    }

    public static String currentRejection() {
        return grid == null ? currentRejection : null;
    }

    public static String gridSource() {
        return gridSource;
    }

    public static boolean analyzing() {
        Thread current = worker;
        return (current != null && current.isAlive()) || prefetchSongId >= 0;
    }

    public static boolean prefetching() {
        return prefetchSongId >= 0;
    }

    public static long prefetchSongId() {
        return prefetchSongId;
    }

    public static int prefetchPending() {
        synchronized (PREFETCH_LOCK) {
            return PREFETCH_QUEUE.size() + (prefetchSongId >= 0 ? 1 : 0);
        }
    }

    public static int cachedEntryCount() {
        return BeatGridStore.count();
    }

    public static long cachedCacheBytes() {
        return BeatGridStore.sizeBytes();
    }

    public static int clearCache() {
        stopPrefetch();
        stop();
        CACHE.clear();
        REJECTIONS.clear();
        REPORTED_REJECTIONS.clear();
        grid = null;
        gridSource = null;
        currentRejection = null;
        int removed = BeatGridStore.clear();
        if (enabled) {
            Music current = CloudMusic.currentlyPlaying;
            AudioPlayer player = CloudMusic.player;
            if (current != null && player != null) {
                onSongStarted(current, player);
            }
        }
        return removed;
    }

    private static void restore(long id) {
        if (CACHE.containsKey(id) || REJECTIONS.containsKey(id)) {
            return;
        }
        MusicBeatGrid stored = BeatGridStore.load(id);
        if (stored == null || !stored.isComplete()) {
            return;
        }
        if (stored.isReliable()) {
            CACHE.put(id, stored);
        } else {
            REJECTIONS.put(id, stored.rejection());
        }
    }

    private static boolean prefetching(long id) {
        return prefetchSongId == id;
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

    private static void stopPrefetch() {
        Thread thread;
        synchronized (PREFETCH_LOCK) {
            prefetchGeneration++;
            PREFETCH_QUEUE.clear();
            PREFETCH_PENDING.clear();
            thread = prefetchWorker;
            prefetchWorker = null;
        }
        AudioPlayer player = prefetchPlayer;
        prefetchPlayer = null;
        prefetchSongId = -1L;
        if (player != null) {
            player.cancelBeatGridAnalysis();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void run(long id, AudioPlayer player) {
        try {
            if (!AutoMixSupport.prepare(MODEL_WAIT_MILLIS)) {
                if (UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
                    AutoMixSupport.State state = AutoMixSupport.state();
                    Platform.log("[NCM] Beat-synced effects unavailable: "
                            + (state.reasonKey() == null ? "unknown" : state.reasonKey())
                            + (state.detail() == null ? "" : " (" + state.detail() + ")"));
                }
                return;
            }
            player.analyzeBeatGrid(candidate -> accept(id, candidate, SOURCE_LIVE));
        } catch (Throwable throwable) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                Platform.log("[NCM] Beat grid analysis failed: " + throwable);
            }
        }
    }

    private static void prefetchLoop() {
        int generation;
        synchronized (PREFETCH_LOCK) {
            generation = prefetchGeneration;
        }
        while (true) {
            Music song;
            synchronized (PREFETCH_LOCK) {
                if (generation != prefetchGeneration) {
                    return;
                }
                song = PREFETCH_QUEUE.pollFirst();
                if (song == null) {
                    prefetchWorker = null;
                    return;
                }
            }
            try {
                analyzePrefetched(song, generation);
            } finally {
                synchronized (PREFETCH_LOCK) {
                    PREFETCH_PENDING.remove(song.getId());
                }
            }
        }
    }

    private static void analyzePrefetched(Music song, int generation) {
        long id = song.getId();
        AudioPlayer player = null;
        prefetchSongId = id;
        try {
            if (!enabled || generation != prefetchGeneration) {
                return;
            }
            awaitLiveAnalysis();
            if (!enabled || generation != prefetchGeneration || !AutoMixSupport.prepare(MODEL_WAIT_MILLIS)) {
                return;
            }
            player = CloudMusic.createAnalysisPlayer(song);
            if (player == null || !enabled || generation != prefetchGeneration) {
                return;
            }
            prefetchPlayer = player;
            player.analyzeBeatGrid(candidate -> accept(id, candidate, SOURCE_PREFETCH));
        } catch (Throwable throwable) {
            if (!Thread.currentThread().isInterrupted() && PREFETCH_FAILURE_LOGGED.compareAndSet(false, true)) {
                Platform.log("[NCM] Beat grid pre-analysis failed: " + throwable);
            }
        } finally {
            long analysed = prefetchSongId;
            prefetchSongId = -1L;
            prefetchPlayer = null;
            if (player != null) {
                player.close();
            }
            resumeCurrentAfterPrefetch(analysed);
        }
    }

    private static void awaitLiveAnalysis() {
        Thread live = worker;
        if (live == null || live == Thread.currentThread()) {
            return;
        }
        try {
            live.join(PREFETCH_LIVE_WAIT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void resumeCurrentAfterPrefetch(long id) {
        if (id < 0 || !enabled || id != songId || REJECTIONS.containsKey(id)) {
            return;
        }
        MusicBeatGrid prefetched = CACHE.get(id);
        if (prefetched != null && prefetched.isComplete()) {
            return;
        }
        Music current = CloudMusic.currentlyPlaying;
        AudioPlayer currentPlayer = CloudMusic.player;
        if (current != null && current.getId() == id && currentPlayer != null) {
            start(current, currentPlayer);
        }
    }

    private static void accept(long id, MusicBeatGrid candidate, String source) {
        if (candidate == null || Thread.currentThread().isInterrupted()) {
            return;
        }
        if (!candidate.isReliable()) {
            if (songId == id) {
                currentRejection = candidate.rejection();
            }
            if (REPORTED_REJECTIONS.size() < MAXIMUM_CACHED_REJECTIONS && REPORTED_REJECTIONS.add(id)) {
                Platform.log("[NCM] Song groove disabled for song " + id + ": " + candidate.rejection());
            }
            if (candidate.isComplete()) {
                REJECTIONS.put(id, candidate.rejection());
                BeatGridStore.save(id, candidate);
            }
            return;
        }
        CACHE.put(id, candidate);
        if (songId == id) {
            grid = candidate;
            gridSource = source;
            currentRejection = null;
        }
        if (candidate.isComplete()) {
            BeatGridStore.save(id, candidate);
        }
    }
}
