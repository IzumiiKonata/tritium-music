package tritium.music.core.util;

import java.time.Duration;

public class Timer {

    public long lastNs = System.nanoTime();

    public Timer() {
    }

    public Timer(long millis) {
        this.lastNs = System.nanoTime() - millis * 1_000_000;
    }

    public boolean isDelayed(long ms) {
        return this.isDelayed(ms * 1_000_000.0);
    }

    public boolean isDelayed(double nanoSeconds) {
        return (System.nanoTime() - this.lastNs) >= nanoSeconds;
    }

    public boolean isDelayed(Duration duration) {
        return System.nanoTime() - lastNs >= duration.toNanos();
    }

    public boolean isDelayed(long nanoSeconds, boolean reset) {
        boolean delayed = this.isDelayed(nanoSeconds);

        if (delayed && reset) {
            this.reset();
            return true;
        }

        return delayed;
    }

    public void reset() {
        this.lastNs = System.nanoTime();
    }

    public Duration delayed() {
        return Duration.ofNanos(System.nanoTime() - lastNs);
    }
}
