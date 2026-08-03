package tritium.music.core.lyric.provider;

import tritium.music.core.lyric.LyricParser;
import tritium.music.platform.Platform;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LyricsFetcher {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final List<LyricsProvider> providers;
    private final Map<LyricsQuery, CompletableFuture<Optional<LyricsResult>>> cache = new LinkedHashMap<>(32, .75f, true);
    private final Map<ProviderQuery, CompletableFuture<Optional<LyricsResult>>> providerCache = new LinkedHashMap<>(128, .75f, true);
    private final Map<LyricsQuery, CompletableFuture<List<AvailableLyrics>>> availabilityCache = new LinkedHashMap<>(32, .75f, true);

    public record AvailableLyrics(String id, String displayName, LyricsResult result, boolean wordTimed) {
    }

    private record ProviderQuery(String providerId, LyricsQuery query) {
    }

    public LyricsFetcher(List<LyricsProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public static LyricsFetcher getDefault() {
        return DefaultHolder.INSTANCE;
    }

    public Optional<LyricsResult> fetch(LyricsQuery query) {
        CompletableFuture<Optional<LyricsResult>> future = request(query, "fetch");
        try {
            return future.join();
        } catch (Exception ignored) {
            removeFailed(query, future);
            return Optional.empty();
        }
    }

    public Optional<LyricsResult> fetch(LyricsQuery query, String providerId) {
        if (providerId == null || providerId.isBlank()) return fetch(query);
        LyricsProvider provider = provider(providerId);
        if (provider == null) return fetch(query);
        Optional<LyricsResult> selected = requestProvider(provider, query).join();
        return selected.isPresent() ? selected : fetch(query);
    }

    public List<AvailableLyrics> available(LyricsQuery query) {
        try {
            return requestAvailability(query).join();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private synchronized CompletableFuture<List<AvailableLyrics>> requestAvailability(LyricsQuery query) {
        CompletableFuture<List<AvailableLyrics>> existing = availabilityCache.get(query);
        if (existing != null) return existing;
        CompletableFuture<List<AvailableLyrics>> created = CompletableFuture.supplyAsync(() -> findAvailable(query), EXECUTOR);
        availabilityCache.put(query, created);
        while (availabilityCache.size() > 32) availabilityCache.remove(availabilityCache.keySet().iterator().next());
        return created;
    }

    private List<AvailableLyrics> findAvailable(LyricsQuery query) {
        List<CompletableFuture<Optional<LyricsResult>>> futures = providers.stream()
                .map(provider -> requestProvider(provider, query))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .join();
        List<AvailableLyrics> available = new ArrayList<>();
        for (int index = 0; index < providers.size(); index++) {
            Optional<LyricsResult> result = futures.get(index).getNow(Optional.empty());
            if (result.isPresent() && !result.get().isEmpty()) {
                LyricsProvider provider = providers.get(index);
                available.add(new AvailableLyrics(
                        provider.id(), provider.displayName(), result.get(), hasWordTimings(result.get())));
            }
        }
        return List.copyOf(available);
    }

    public void prefetch(LyricsQuery query) {
        request(query, "prefetch");
    }

    private synchronized CompletableFuture<Optional<LyricsResult>> request(LyricsQuery query, String reason) {
        CompletableFuture<Optional<LyricsResult>> existing = cache.get(query);
        if (existing != null) {
            log(reason + " cache hit, state=" + (existing.isDone() ? "ready" : "loading") + ", " + describe(query));
            return existing;
        }
        log(reason + " cache miss, starting providers, " + describe(query));
        CompletableFuture<Optional<LyricsResult>> created = CompletableFuture.supplyAsync(() -> fetchUncached(query), EXECUTOR);
        cache.put(query, created);
        while (cache.size() > 32) {
            LyricsQuery eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
        created.whenComplete((result, error) -> {
            if (error != null || result == null || result.isEmpty()) removeFailed(query, created);
        });
        return created;
    }

    private synchronized void removeFailed(LyricsQuery query, CompletableFuture<Optional<LyricsResult>> future) {
        if (cache.get(query) == future) cache.remove(query);
    }

    private Optional<LyricsResult> fetchUncached(LyricsQuery query) {
        long started = System.nanoTime();
        List<CompletableFuture<Optional<LyricsResult>>> futures = providers.stream()
                .map(provider -> requestProvider(provider, query))
                .toList();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        List<Optional<LyricsResult>> completed = new ArrayList<>(providers.size());
        for (int i = 0; i < providers.size(); i++) completed.add(null);
        while (System.nanoTime() < deadline) {
            boolean changed = false;
            for (int i = 0; i < futures.size(); i++) {
                CompletableFuture<Optional<LyricsResult>> future = futures.get(i);
                if (completed.get(i) == null && future.isDone()) {
                    completed.set(i, future.getNow(Optional.empty()));
                    logProviderResult(providers.get(i), completed.get(i), started, query);
                    changed = true;
                }
            }
            if (changed) {
                int timedIndex = firstTimedResult(completed);
                if (timedIndex >= 0 && higherPrioritiesCompleted(completed, timedIndex)) {
                    Optional<LyricsResult> result = completed.get(timedIndex);
                    log("selected timed lyrics from " + result.orElseThrow().source() + " after " + elapsedMillis(started) + "ms, " + describe(query));
                    return result;
                }
                if (completed.stream().noneMatch(Objects::isNull)) break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        int timedIndex = firstTimedResult(completed);
        if (timedIndex >= 0) {
            LyricsResult result = completed.get(timedIndex).orElseThrow();
            log("selected timed lyrics from " + result.source() + " at timeout after " + elapsedMillis(started) + "ms, " + describe(query));
            return Optional.of(result);
        }
        for (Optional<LyricsResult> result : completed) {
            if (result != null && result.isPresent() && !result.get().isEmpty()) {
                log("fallback to non-timed lyrics from " + result.get().source() + " after " + elapsedMillis(started) + "ms, " + describe(query));
                return result;
            }
        }
        log("no provider returned usable lyrics after " + elapsedMillis(started) + "ms, " + describe(query));
        return Optional.empty();
    }

    private synchronized CompletableFuture<Optional<LyricsResult>> requestProvider(LyricsProvider provider, LyricsQuery query) {
        ProviderQuery key = new ProviderQuery(provider.id(), query);
        CompletableFuture<Optional<LyricsResult>> existing = providerCache.get(key);
        if (existing != null) return existing;
        CompletableFuture<Optional<LyricsResult>> created = CompletableFuture.supplyAsync(() -> search(provider, query), EXECUTOR);
        providerCache.put(key, created);
        while (providerCache.size() > 128) providerCache.remove(providerCache.keySet().iterator().next());
        created.whenComplete((result, error) -> {
            if (error != null || result == null || result.isEmpty()) removeFailedProvider(key, created);
        });
        return created;
    }

    private synchronized void removeFailedProvider(ProviderQuery key, CompletableFuture<Optional<LyricsResult>> future) {
        if (providerCache.get(key) == future) providerCache.remove(key);
    }

    private LyricsProvider provider(String providerId) {
        return providers.stream().filter(provider -> provider.id().equals(providerId)).findFirst().orElse(null);
    }

    private static Optional<LyricsResult> search(LyricsProvider provider, LyricsQuery query) {
        try {
            return provider.search(query);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasWordTimings(LyricsResult result) {
        try {
            return LyricParser.parse(result).stream().anyMatch(line -> !line.words.isEmpty());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int firstTimedResult(List<Optional<LyricsResult>> completed) {
        for (int i = 0; i < completed.size(); i++) {
            Optional<LyricsResult> result = completed.get(i);
            if (result != null && result.isPresent() && !result.get().isEmpty() && hasWordTimings(result.get()))
                return i;
        }
        return -1;
    }

    private static boolean higherPrioritiesCompleted(List<Optional<LyricsResult>> completed, int resultIndex) {
        for (int i = 0; i < resultIndex; i++) {
            if (completed.get(i) == null) return false;
        }
        return true;
    }

    static void log(String message) {
        try {
            Platform.log("[Lyrics] " + message);
        } catch (Exception ignored) {
            System.out.println("[Lyrics] " + message);
        }
    }

    private static void logProviderResult(LyricsProvider provider, Optional<LyricsResult> result, long started, LyricsQuery query) {
        String providerName = provider.getClass().getSimpleName();
        if (result.isEmpty() || result.get().isEmpty()) {
            log(providerName + " returned empty after " + elapsedMillis(started) + "ms, " + describe(query));
            return;
        }
        LyricsResult value = result.get();
        log(providerName + " returned source=" + value.source() + ", format=" + value.format()
                + ", timed=" + hasWordTimings(value) + ", chars=" + value.lyrics().length()
                + ", elapsed=" + elapsedMillis(started) + "ms, " + describe(query));
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String describe(LyricsQuery query) {
        return "id=" + query.songId() + ", title=\"" + query.title() + "\"";
    }

    private static final class DefaultHolder {
        private static final LyricsFetcher INSTANCE = new LyricsFetcher(List.of(
                new LocalLyricsProvider(),
                new AmllLyricsProvider(),
                new QqLyricsProvider(),
                new NeteaseLyricsProvider()
        ));
    }
}
