package tritium.music.core.audio;

import com.google.gson.Gson;
import tritium.music.platform.Platform;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class BeatGridStore {

    private static final int VERSION = 1;
    private static final int MAXIMUM_ENTRIES = 1500;
    private static final String DIRECTORY = "beat-grids";
    private static final String SUFFIX = ".json";
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final AtomicInteger ENTRY_COUNT = new AtomicInteger(-1);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private BeatGridStore() {
    }

    private record Entry(int version, long songId, MusicBeatGrid.Snapshot snapshot) {
    }

    public static MusicBeatGrid load(long songId) {
        synchronized (LOCK) {
            Path path = path(songId);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                Entry entry = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Entry.class);
                if (entry == null || entry.version() != VERSION || entry.songId() != songId) {
                    delete(path);
                    return null;
                }
                MusicBeatGrid grid = MusicBeatGrid.restore(entry.snapshot());
                if (grid == null) {
                    delete(path);
                    return null;
                }
                return grid;
            } catch (Exception exception) {
                delete(path);
                return null;
            }
        }
    }

    public static void save(long songId, MusicBeatGrid grid) {
        if (grid == null || !grid.isComplete()) {
            return;
        }
        synchronized (LOCK) {
            Path path = path(songId);
            try {
                boolean existing = Files.isRegularFile(path);
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(new Entry(VERSION, songId, grid.snapshot())), StandardCharsets.UTF_8);
                if (!existing && ENTRY_COUNT.get() >= 0) {
                    ENTRY_COUNT.incrementAndGet();
                }
                trim();
            } catch (Exception exception) {
                if (FAILURE_LOGGED.compareAndSet(false, true)) {
                    Platform.log("[NCM] Beat grid cache write failed: " + exception.getMessage());
                }
            }
        }
    }

    public static boolean contains(long songId) {
        synchronized (LOCK) {
            return Files.isRegularFile(path(songId));
        }
    }

    public static int count() {
        int cached = ENTRY_COUNT.get();
        if (cached >= 0) {
            return cached;
        }
        synchronized (LOCK) {
            if (ENTRY_COUNT.get() < 0) {
                ENTRY_COUNT.set(files().size());
            }
            return Math.max(0, ENTRY_COUNT.get());
        }
    }

    public static long sizeBytes() {
        synchronized (LOCK) {
            long total = 0;
            for (Path path : files()) {
                try {
                    total += Files.size(path);
                } catch (Exception ignored) {
                }
            }
            return total;
        }
    }

    public static int clear() {
        synchronized (LOCK) {
            List<Path> files = files();
            int removed = 0;
            for (Path path : files) {
                if (delete(path)) {
                    removed++;
                }
            }
            ENTRY_COUNT.set(0);
            return removed;
        }
    }

    private static void trim() {
        int count = count();
        if (count <= MAXIMUM_ENTRIES) {
            return;
        }
        List<Path> files = files();
        List<PathWithTime> entries = new ArrayList<>(files.size());
        for (Path path : files) {
            try {
                entries.add(new PathWithTime(path, Files.getLastModifiedTime(path).toMillis()));
            } catch (Exception ignored) {
            }
        }
        entries.sort(Comparator.comparingLong(PathWithTime::modified));
        int remove = count - MAXIMUM_ENTRIES;
        int removed = 0;
        for (PathWithTime entry : entries) {
            if (removed >= remove) {
                break;
            }
            if (delete(entry.path())) {
                removed++;
            }
        }
        ENTRY_COUNT.set(Math.max(0, ENTRY_COUNT.get() - removed));
    }

    private static List<Path> files() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (Exception exception) {
            return false;
        }
    }

    private static Path path(long songId) {
        return directory().resolve(songId + SUFFIX);
    }

    private static Path directory() {
        return new File(Platform.configDir(), DIRECTORY).toPath();
    }

    private record PathWithTime(Path path, long modified) {
    }
}
