package tritium.music.core.audio;

public final class AutoMixTempoPolicy {
    public static final double MAX_TEMPO_MATCH_CHANGE = 0.14;
    private static final double HIGH_STRETCH_THRESHOLD = 0.10;

    private AutoMixTempoPolicy() {
    }

    public static boolean shouldSync(AutoMixTrackAnalysis currentAnalysis, AutoMixTrackAnalysis nextAnalysis, double rate) {
        AutoMixProfile current = currentAnalysis.profile();
        AutoMixProfile next = nextAnalysis.profile();
        double change = Math.abs(rate - 1);
        if (!current.downbeatAware() || !next.downbeatAware() || current.beatConfidence() < 0.78 || next.beatConfidence() < 0.78 || change < 0.025 || change > MAX_TEMPO_MATCH_CHANGE || change > HIGH_STRETCH_THRESHOLD && (current.beatConfidence() < 0.92 || next.beatConfidence() < 0.92) || currentAnalysis.endingType() == AutoMixTrackAnalysis.EndingType.NATURAL_FADE || Math.abs(current.loudnessDb() - next.loudnessDb()) > 10 || nextAnalysis.firstSoundMillis() > 2_500 || nextAnalysis.firstStrongMillis() - nextAnalysis.firstSoundMillis() > 3_500) {
            return false;
        }
        long rhythmicGap = currentAnalysis.lastSoundMillis() - current.lastOnsetMillis();
        long maximumGap = Math.max(2_800, Math.round(current.beatIntervalMillis() * 6));
        return current.lastOnsetMillis() > 0 && rhythmicGap >= 0 && rhythmicGap <= maximumGap;
    }
}
