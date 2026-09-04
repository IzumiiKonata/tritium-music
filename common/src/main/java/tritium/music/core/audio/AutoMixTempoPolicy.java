package tritium.music.core.audio;

public final class AutoMixTempoPolicy {
    public static double MAX_TEMPO_MATCH_CHANGE = .1;

    private AutoMixTempoPolicy() {
    }

    public static boolean shouldSync(AutoMixTrackAnalysis currentAnalysis, AutoMixTrackAnalysis nextAnalysis, double rate) {
        AutoMixProfile current = currentAnalysis.profile();
        AutoMixProfile next = nextAnalysis.profile();
        double change = Math.abs(rate - 1);
        if (!current.downbeatAware() || !next.downbeatAware() || current.beatConfidence() < 0.86 || next.beatConfidence() < 0.86 || change < 0.008 || change > MAX_TEMPO_MATCH_CHANGE || change > 0.06 && (current.beatConfidence() < 0.94 || next.beatConfidence() < 0.94) || currentAnalysis.endingType() == AutoMixTrackAnalysis.EndingType.NATURAL_FADE || Math.abs(current.loudnessDb() - next.loudnessDb()) > 10 || nextAnalysis.firstSoundMillis() > 4_000 || nextAnalysis.firstStrongMillis() - nextAnalysis.firstSoundMillis() > 5_000) {
            return false;
        }
        long rhythmicGap = currentAnalysis.lastSoundMillis() - current.lastOnsetMillis();
        long maximumGap = Math.max(2_800, Math.round(current.beatIntervalMillis() * 6));
        return current.lastOnsetMillis() > 0 && rhythmicGap >= 0 && rhythmicGap <= maximumGap;
    }
}
