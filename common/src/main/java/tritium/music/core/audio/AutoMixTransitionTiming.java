package tritium.music.core.audio;

public final class AutoMixTransitionTiming {
    private static final long DEFAULT_OVERLAP_MILLIS = 6_000;
    private static final long MINIMUM_OVERLAP_MILLIS = 900;

    private AutoMixTransitionTiming() {
    }

    public static Window fallback(long trackDurationMillis, long currentPositionMillis) {
        long end = Math.max(0, trackDurationMillis);
        long overlap = Math.min(DEFAULT_OVERLAP_MILLIS, Math.max(MINIMUM_OVERLAP_MILLIS, end / 4));
        long start = Math.max(0, end - overlap);
        return fit(new Window(start, Math.max(MINIMUM_OVERLAP_MILLIS, end - start)), currentPositionMillis, end);
    }

    public static Window fit(Window preferred, long currentPositionMillis, long endMillis) {
        long end = Math.max(0, endMillis);
        long position = Math.max(0, currentPositionMillis);
        if (position <= preferred.startMillis() + 100) {
            long duration = Math.min(preferred.durationMillis(), Math.max(1, end - preferred.startMillis()));
            return new Window(preferred.startMillis(), Math.max(1, duration));
        }
        long start = Math.min(position, Math.max(0, end - 1));
        long remaining = Math.max(1, end - start);
        return new Window(start, Math.min(preferred.durationMillis(), remaining));
    }

    public record Window(long startMillis, long durationMillis) {
        public Window {
            startMillis = Math.max(0, startMillis);
            durationMillis = Math.max(1, durationMillis);
        }
    }
}
