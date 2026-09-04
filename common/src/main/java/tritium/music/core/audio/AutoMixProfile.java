package tritium.music.core.audio;

public record AutoMixProfile(long cueInMillis, double beatIntervalMillis, double beatPhaseMillis, double beatConfidence,
                             double loudnessDb, double recentLoudnessDb, long quietDurationMillis, long analyzedMillis,
                             long lastOnsetMillis, boolean downbeatAware, double downbeatIntervalMillis,
                             double downbeatPhaseMillis) {

    public static AutoMixProfile fallback() {
        return new AutoMixProfile(0, 0, 0, 0, -18, -18, 0, 0, 0, false, 0, 0);
    }

    public AutoMixProfile withBeatGrid(double intervalMillis, double phaseMillis, double confidence, boolean downbeatAware, double downbeatIntervalMillis, double downbeatPhaseMillis) {
        long alignedCue = cueInMillis;
        if (intervalMillis > 0) {
            double beat = Math.ceil((cueInMillis - phaseMillis) / intervalMillis);
            long candidate = Math.max(0, Math.round(phaseMillis + beat * intervalMillis));
            if (candidate <= cueInMillis + intervalMillis * 1.5) {
                alignedCue = Math.min(12_000, candidate);
            }
        }
        return new AutoMixProfile(alignedCue, intervalMillis, phaseMillis, confidence, loudnessDb, recentLoudnessDb, quietDurationMillis, analyzedMillis, lastOnsetMillis, downbeatAware, downbeatIntervalMillis, downbeatPhaseMillis);
    }

    public boolean hasReliableBeat() {
        return beatIntervalMillis >= 250 && beatIntervalMillis <= 1500 && beatConfidence >= 0.16;
    }

    public long transitionDurationMillis() {
        if (!hasReliableBeat()) {
            return 8_000;
        }
        return Math.round(Math.max(6_000, Math.min(12_000, beatIntervalMillis * 16)));
    }

    public long alignToBeat(long timeMillis) {
        if (!hasReliableBeat()) {
            return timeMillis;
        }
        double beat = Math.rint((timeMillis - beatPhaseMillis) / beatIntervalMillis);
        return Math.max(0, Math.round(beatPhaseMillis + beat * beatIntervalMillis));
    }

    public long nextBeatAfter(long timeMillis) {
        if (!hasReliableBeat()) {
            return timeMillis;
        }
        double beat = Math.ceil((timeMillis - beatPhaseMillis) / beatIntervalMillis);
        return Math.max(0, Math.round(beatPhaseMillis + beat * beatIntervalMillis));
    }

    public long alignToDownbeat(long timeMillis) {
        if (!hasReliableDownbeat()) {
            return alignToBeat(timeMillis);
        }
        double downbeat = Math.rint((timeMillis - downbeatPhaseMillis) / downbeatIntervalMillis);
        return Math.max(0, Math.round(downbeatPhaseMillis + downbeat * downbeatIntervalMillis));
    }

    public long downbeatAtOrBefore(long timeMillis) {
        if (!hasReliableDownbeat()) {
            if (!hasReliableBeat()) {
                return timeMillis;
            }
            double beat = Math.floor((timeMillis - beatPhaseMillis) / beatIntervalMillis);
            return Math.max(0, Math.round(beatPhaseMillis + beat * beatIntervalMillis));
        }
        double downbeat = Math.floor((timeMillis - downbeatPhaseMillis) / downbeatIntervalMillis);
        return Math.max(0, Math.round(downbeatPhaseMillis + downbeat * downbeatIntervalMillis));
    }

    private boolean hasReliableDownbeat() {
        return downbeatAware && downbeatIntervalMillis >= beatIntervalMillis * 1.5 && downbeatIntervalMillis <= beatIntervalMillis * 8;
    }

    public boolean isTempoCompatible(AutoMixProfile other, double maximumRateChange) {
        if (!hasReliableBeat() || !other.hasReliableBeat()) {
            return false;
        }
        double ratio = normalizedTempoRatio(other);
        return ratio >= 1 - maximumRateChange && ratio <= 1 + maximumRateChange;
    }

    public double beatMatchRateTo(AutoMixProfile other, double maximumRateChange) {
        if (!isTempoCompatible(other, maximumRateChange)) {
            return 1;
        }
        return normalizedTempoRatio(other);
    }

    private double normalizedTempoRatio(AutoMixProfile other) {
        double ratio = beatIntervalMillis / other.beatIntervalMillis;
        while (ratio < 0.75) {
            ratio *= 2;
        }
        while (ratio > 1.5) {
            ratio *= 0.5;
        }
        return ratio;
    }
}
