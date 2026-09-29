package tritium.music.core.audio;

import tritium.music.core.assets.AssetCatalog;
import tritium.music.core.assets.AssetManager;
import tritium.music.core.assets.AssetPlatform;
import tritium.music.core.assets.RemoteAsset;
import tritium.music.platform.Platform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AutoMixSupport {

    public static final String REASON_PLATFORM = "tritium-music.ui.feature.reason.platform";
    public static final String REASON_DOWNLOAD = "tritium-music.ui.feature.reason.download";
    public static final String REASON_RUNTIME = "tritium-music.ui.feature.reason.runtime";

    public record State(boolean available, boolean terminal, String reasonKey, String detail) {

        public boolean disabled() {
            return !available;
        }
    }

    private static final List<Consumer<State>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static final Object LOCK = new Object();

    private static volatile boolean runtimeFailed;
    private static volatile String runtimeDetail;
    private static volatile boolean nativePathConfigured;
    private static volatile State state = new State(false, false, REASON_DOWNLOAD, null);

    private AutoMixSupport() {
    }

    public static State state() {
        ensureListening();
        publish();
        return state;
    }

    public static boolean isAvailable() {
        return state().available();
    }

    public static boolean isTerminal() {
        return state().terminal();
    }

    public static void addListener(Consumer<State> listener) {
        LISTENERS.add(listener);
        ensureListening();
        listener.accept(state());
    }

    public static boolean prepare(long timeoutMillis) {
        ensureListening();
        if (!AssetPlatform.isSupported()) {
            publish();
            return false;
        }
        if (runtimeFailed) {
            publish();
            return false;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
        for (RemoteAsset asset : required()) {
            if (AssetManager.get().isReady(asset)) {
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            AssetManager.get().awaitReady(asset.path(), TimeUnit.NANOSECONDS.toMillis(remaining));
        }

        if (!ready()) {
            publish();
            return false;
        }

        if (!nativePathConfigured) {
            synchronized (LOCK) {
                if (!nativePathConfigured) {
                    File directory = AssetManager.get().directoryOf(AssetCatalog.activeNatives());
                    if (directory == null) {
                        publish();
                        return false;
                    }
                    System.setProperty("onnxruntime.native.path", directory.getAbsolutePath());
                    nativePathConfigured = true;
                    log("onnxruntime.native.path=" + directory.getAbsolutePath());
                }
            }
        }

        publish();
        return true;
    }

    public static void reportRuntimeFailure(Throwable throwable) {
        if (runtimeFailed) {
            return;
        }
        runtimeFailed = true;
        runtimeDetail = throwable == null ? null : throwable.toString();
        log("AutoMix runtime unavailable: " + runtimeDetail);
        publish();
    }

    private static List<RemoteAsset> required() {
        List<RemoteAsset> assets = new ArrayList<>(AssetCatalog.activeNatives());
        assets.add(AssetCatalog.find(AssetCatalog.MODEL_MEL_SPECTROGRAM));
        assets.add(AssetCatalog.find(AssetCatalog.MODEL_BEAT_THIS));
        assets.removeIf(asset -> asset == null);
        return assets;
    }

    private static boolean ready() {
        for (RemoteAsset asset : required()) {
            if (!AssetManager.get().isReady(asset)) {
                return false;
            }
        }
        return true;
    }

    private static void ensureListening() {
        if (LISTENING.compareAndSet(false, true)) {
            AssetManager.get().addListener(snapshot -> publish());
        }
    }

    private static void publish() {
        State next = current();
        State previous = state;
        boolean changed = previous.available() != next.available()
                || previous.terminal() != next.terminal()
                || !equal(previous.reasonKey(), next.reasonKey());
        state = next;
        if (!changed) {
            return;
        }
        for (Consumer<State> listener : LISTENERS) {
            try {
                listener.accept(next);
            } catch (Throwable ignored) {
            }
        }
    }

    private static State current() {
        if (!AssetPlatform.isSupported()) {
            return new State(false, true, REASON_PLATFORM, AssetPlatform.osName() + " / " + AssetPlatform.osArch());
        }
        if (runtimeFailed) {
            return new State(false, true, REASON_RUNTIME, runtimeDetail);
        }
        boolean ready = ready();
        if (ready) {
            return new State(true, false, null, null);
        }
        AssetManager.Snapshot snapshot = AssetManager.get().snapshot();
        return new State(false, snapshot.unavailable(), REASON_DOWNLOAD, snapshot.failure());
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void log(String message) {
        try {
            Platform.log("[automix] " + message);
        } catch (Throwable ignored) {
        }
    }
}
