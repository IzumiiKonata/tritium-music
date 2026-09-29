package tritium.music.core.assets;

import tritium.music.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AssetManager {

    public enum Phase {
        IDLE,
        RESOLVING,
        DOWNLOADING,
        COMPLETE,
        FAILED
    }

    public enum State {
        PENDING,
        DOWNLOADING,
        VERIFYING,
        READY,
        FAILED
    }

    public record FileStatus(RemoteAsset asset, State state, long downloaded, long total, String error) {

        public double fraction() {
            if (state == State.READY) {
                return 1;
            }
            long size = total > 0 ? total : asset.size();
            return size <= 0 ? 0 : Math.min(1, downloaded / (double) size);
        }
    }

    public record Snapshot(Phase phase, List<FileStatus> files, long bytesDone, long bytesTotal,
                           double bytesPerSecond, AssetRoute route, String failure,
                           boolean essentialReady, boolean complete) {

        public double fraction() {
            if (bytesTotal <= 0) {
                return complete ? 1 : 0;
            }
            return Math.min(1, bytesDone / (double) bytesTotal);
        }

        public long remainingBytes() {
            return Math.max(0, bytesTotal - bytesDone);
        }

        public double remainingSeconds() {
            return bytesPerSecond <= 1 ? -1 : remainingBytes() / bytesPerSecond;
        }

        public boolean active() {
            return phase == Phase.RESOLVING || phase == Phase.DOWNLOADING;
        }

        public boolean failed() {
            return phase == Phase.FAILED;
        }

        public int readyCount() {
            int count = 0;
            for (FileStatus file : files) {
                if (file.state() == State.READY) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final AssetManager INSTANCE = new AssetManager();

    private static final long SPEED_WINDOW_NANOS = 250_000_000L;
    private static final int PROBE_TIMEOUT_SECONDS = 6;
    private static final long RETRY_COOLDOWN_MILLIS = 60_000L;
    private static final long POLL_INTERVAL_MILLIS = 120L;
    private static final long SCAN_INTERVAL_MILLIS = 1000L;
    private static final long SLOW_ROUTE_GRACE_NANOS = 20_000_000_000L;
    private static final long SLOW_ROUTE_MIN_BYTES = 1_000_000L;
    private static final double SLOW_ROUTE_BYTES_PER_SECOND = 10_000;

    private final List<Entry> entries = new ArrayList<>();
    private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object storeLock = new Object();

    private volatile AssetStore store;
    private volatile boolean scanned;
    private volatile Phase phase = Phase.IDLE;
    private volatile AssetRoute route;
    private volatile String failure;
    private volatile long lastFailureMillis;
    private volatile double bytesPerSecond;
    private volatile long sampleNanos = System.nanoTime();
    private volatile long sampleBytes;
    private long slowRouteStartNanos = System.nanoTime();
    private boolean slowRouteAbortAllowed;
    private volatile long lastScanMillis;

    private AssetManager() {
        for (RemoteAsset asset : AssetCatalog.all()) {
            entries.add(new Entry(asset));
        }
    }

    public static AssetManager get() {
        return INSTANCE;
    }

    public void start() {
        if (!ensureScanned()) {
            setFailure("asset storage unavailable");
            return;
        }
        if (!needsDownload()) {
            setPhase(Phase.COMPLETE);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::run, "tritium-asset-downloader");
        worker.setDaemon(true);
        worker.start();
    }

    public void retry() {
        if (running.get()) {
            return;
        }
        for (Entry entry : entries) {
            if (entry.state == State.FAILED) {
                entry.state = State.PENDING;
                entry.error = null;
            }
        }
        failure = null;
        phase = Phase.IDLE;
        notifyListeners();
        start();
    }

    public boolean needsDownload() {
        if (!ensureScanned()) {
            return true;
        }
        for (Entry entry : entries) {
            if (!isReady(entry.asset)) {
                return true;
            }
        }
        return false;
    }

    public boolean isReady(String path) {
        RemoteAsset asset = AssetCatalog.find(path);
        return asset != null && isReady(asset);
    }

    public boolean isReady(RemoteAsset asset) {
        if (!ensureScanned()) {
            return false;
        }
        return store.isReady(asset);
    }

    public File file(String path) {
        RemoteAsset asset = AssetCatalog.find(path);
        if (asset == null || !ensureScanned()) {
            return null;
        }
        return store.file(asset);
    }

    public boolean awaitReady(String path, long timeoutMillis) {
        RemoteAsset asset = AssetCatalog.find(path);
        if (asset == null) {
            return false;
        }
        if (isReady(asset)) {
            return true;
        }
        if (needsDownload()) {
            start();
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
        while (!Thread.currentThread().isInterrupted()) {
            if (isReady(asset)) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            if (phase == Phase.FAILED) {
                long sinceFailure = System.currentTimeMillis() - lastFailureMillis;
                if (sinceFailure >= RETRY_COOLDOWN_MILLIS) {
                    retry();
                } else {
                    sleep(Math.min(RETRY_COOLDOWN_MILLIS - sinceFailure, TimeUnit.NANOSECONDS.toMillis(remaining)));
                }
                continue;
            }
            sleep(Math.min(POLL_INTERVAL_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining)));
        }
        return isReady(asset);
    }

    public void addListener(Consumer<Snapshot> listener) {
        listeners.add(listener);
    }

    public Snapshot snapshot() {
        ensureScanned();
        refreshEntries();
        List<FileStatus> files = new ArrayList<>(entries.size());
        long done = 0;
        long total = 0;
        boolean essentialReady = true;
        boolean complete = true;
        for (Entry entry : entries) {
            RemoteAsset asset = entry.asset;
            State state = entry.state;
            long downloaded = state == State.READY ? asset.size() : Math.min(entry.downloaded, asset.size());
            files.add(new FileStatus(asset, state, downloaded, asset.size(), entry.error));
            done += downloaded;
            total += asset.size();
            if (state != State.READY) {
                complete = false;
                if (asset.essential()) {
                    essentialReady = false;
                }
            }
        }
        return new Snapshot(phase, List.copyOf(files), done, total, bytesPerSecond, route, failure,
                essentialReady, complete);
    }

    private void run() {
        Set<String> excluded = new HashSet<>();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<Entry> missing = missingEntries();
                if (missing.isEmpty()) {
                    setPhase(Phase.COMPLETE);
                    return;
                }
                log("missing " + missing.size() + " file(s), " + AssetFormat.bytes(missingBytes(missing)));
                setPhase(Phase.RESOLVING);
                AssetRoute resolved = resolveRoute(missing.getFirst().asset, excluded);
                if (resolved == null) {
                    setFailure("all download routes failed");
                    return;
                }
                route = resolved;
                failure = null;
                log("route " + resolved.displayName() + " " + resolved.prefix());
                setPhase(Phase.DOWNLOADING);
                boolean slowAbortAllowed = !resolved.mirror() && hasUntriedMirror(excluded);
                boolean routeFailed = false;
                for (Entry entry : missing) {
                    if (isReady(entry.asset)) {
                        markReady(entry);
                        continue;
                    }
                    if (!download(entry, resolved, slowAbortAllowed)) {
                        routeFailed = true;
                        break;
                    }
                }
                if (!routeFailed) {
                    setPhase(Phase.COMPLETE);
                    return;
                }
                excluded.add(resolved.prefix());
                if (excluded.size() >= AssetRoute.candidates().size()) {
                    setFailure("all download routes failed");
                    return;
                }
            }
        } catch (Throwable throwable) {
            setFailure(messageOf(throwable));
        } finally {
            running.set(false);
        }
    }

    private boolean download(Entry entry, AssetRoute resolved, boolean slowAbortAllowed) {
        RemoteAsset asset = entry.asset;
        entry.state = State.DOWNLOADING;
        entry.error = null;
        entry.downloaded = 0;
        notifyListeners();

        Path target = store.target(asset).toPath();
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(part);
            slowRouteStartNanos = System.nanoTime();
            slowRouteAbortAllowed = slowAbortAllowed;
            AssetDownloader.download(resolved, asset, part, (chunk, total) -> onChunk(entry, chunk, total));
            entry.state = State.VERIFYING;
            notifyListeners();
            if (!store.install(asset, part)) {
                Files.deleteIfExists(part);
                store.discard(asset);
                throw new IOException("checksum mismatch for " + asset.fileName());
            }
            markReady(entry);
            log("ready " + asset.fileName() + " (" + AssetFormat.bytes(asset.size()) + ")");
            notifyListeners();
            return true;
        } catch (Throwable throwable) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
            entry.state = State.FAILED;
            entry.error = messageOf(throwable);
            failure = asset.fileName() + ": " + entry.error;
            log("failed " + failure);
            notifyListeners();
            return false;
        }
    }

    private AssetRoute resolveRoute(RemoteAsset probe, Set<String> excluded) {
        List<AssetRoute> candidates = new ArrayList<>();
        for (AssetRoute candidate : AssetRoute.candidates()) {
            if (!excluded.contains(candidate.prefix())) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        List<Callable<Boolean>> tasks = new ArrayList<>(candidates.size());
        for (AssetRoute candidate : candidates) {
            tasks.add(() -> AssetDownloader.reachable(candidate, probe));
        }

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Boolean>> futures = pool.invokeAll(tasks, PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (int index = 0; index < candidates.size(); index++) {
                Future<Boolean> future = futures.get(index);
                if (future.isCancelled()) {
                    continue;
                }
                try {
                    if (Boolean.TRUE.equals(future.get())) {
                        return candidates.get(index);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    private void onChunk(Entry entry, int chunk, long total) {
        entry.downloaded += chunk;
        long now = System.nanoTime();
        long elapsed = now - sampleNanos;
        if (elapsed < SPEED_WINDOW_NANOS) {
            return;
        }
        long bytes = downloadedBytes();
        double instant = Math.max(0, (bytes - sampleBytes) / (elapsed / 1_000_000_000.0));
        bytesPerSecond = bytesPerSecond <= 0 ? instant : bytesPerSecond * 0.65 + instant * 0.35;
        sampleBytes = bytes;
        sampleNanos = now;
        if (slowRouteAbortAllowed
                && now - slowRouteStartNanos > SLOW_ROUTE_GRACE_NANOS
                && entry.downloaded > SLOW_ROUTE_MIN_BYTES
                && instant < SLOW_ROUTE_BYTES_PER_SECOND) {
            throw new SlowRouteException();
        }
    }

    private static boolean hasUntriedMirror(Set<String> excluded) {
        for (AssetRoute candidate : AssetRoute.candidates()) {
            if (candidate.mirror() && !excluded.contains(candidate.prefix())) {
                return true;
            }
        }
        return false;
    }

    private static final class SlowRouteException extends RuntimeException {

        private SlowRouteException() {
            super("download route is too slow");
        }
    }

    private void sampleSpeedOnStart() {
        sampleBytes = downloadedBytes();
        sampleNanos = System.nanoTime();
        bytesPerSecond = 0;
    }

    private long downloadedBytes() {
        long bytes = 0;
        for (Entry entry : entries) {
            bytes += entry.state == State.READY ? entry.asset.size() : Math.min(entry.downloaded, entry.asset.size());
        }
        return bytes;
    }

    private List<Entry> missingEntries() {
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : entries) {
            if (isReady(entry.asset)) {
                markReady(entry);
            } else {
                missing.add(entry);
            }
        }
        return missing;
    }

    private void markReady(Entry entry) {
        entry.state = State.READY;
        entry.error = null;
        entry.downloaded = entry.asset.size();
    }

    private void setPhase(Phase next) {
        if (phase == next) {
            return;
        }
        phase = next;
        if (next == Phase.DOWNLOADING) {
            sampleSpeedOnStart();
        }
        notifyListeners();
    }

    private void setFailure(String message) {
        failure = message;
        lastFailureMillis = System.currentTimeMillis();
        for (Entry entry : entries) {
            if (entry.state != State.READY) {
                entry.state = State.FAILED;
                entry.error = message;
            }
        }
        phase = Phase.FAILED;
        log("failed: " + message);
        notifyListeners();
    }

    private long missingBytes(List<Entry> missing) {
        long bytes = 0;
        for (Entry entry : missing) {
            bytes += entry.asset.size();
        }
        return bytes;
    }

    private static void log(String message) {
        try {
            Platform.log("[asset] " + message);
        } catch (Throwable ignored) {
        }
    }

    private void notifyListeners() {
        Snapshot current = snapshot();
        for (Consumer<Snapshot> listener : listeners) {
            try {
                listener.accept(current);
            } catch (Throwable ignored) {
            }
        }
    }

    private void refreshEntries() {
        if (phase == Phase.RESOLVING || phase == Phase.DOWNLOADING) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < SCAN_INTERVAL_MILLIS) {
            return;
        }
        lastScanMillis = now;
        for (Entry entry : entries) {
            if (entry.state != State.READY && isReady(entry.asset)) {
                markReady(entry);
            }
        }
    }

    private boolean ensureScanned() {
        if (scanned) {
            return store != null;
        }
        synchronized (storeLock) {
            if (!scanned) {
                File configDir;
                try {
                    configDir = Platform.configDir();
                } catch (Throwable throwable) {
                    return false;
                }
                store = AssetStore.create(new File(configDir, "assets"), configDir.getParentFile());
                scanned = true;
                for (Entry entry : entries) {
                    if (store.isReady(entry.asset)) {
                        markReady(entry);
                    }
                }
            }
        }
        return true;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static final class Entry {

        private final RemoteAsset asset;
        private volatile State state = State.PENDING;
        private volatile long downloaded;
        private volatile String error;

        private Entry(RemoteAsset asset) {
            this.asset = asset;
        }
    }
}
