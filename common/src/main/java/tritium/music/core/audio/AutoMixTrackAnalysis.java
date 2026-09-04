package tritium.music.core.audio;

public record AutoMixTrackAnalysis(AutoMixProfile profile, long firstSoundMillis, long firstStrongMillis,
                                   long fadeOutStartMillis, long lastStrongMillis, long lastSoundMillis,
                                   EndingType endingType, double endingConfidence, MusicalTimeline timeline) {

    public AutoMixTrackAnalysis(AutoMixProfile profile, long firstSoundMillis, long firstStrongMillis, long fadeOutStartMillis, long lastStrongMillis, long lastSoundMillis, EndingType endingType, double endingConfidence) {
        this(profile, firstSoundMillis, firstStrongMillis, fadeOutStartMillis, lastStrongMillis, lastSoundMillis, endingType, endingConfidence, MusicalTimeline.empty());
    }

    public static AutoMixTrackAnalysis fallback(long durationMillis) {
        return new AutoMixTrackAnalysis(AutoMixProfile.fallback(), 0, 0, Math.max(0, durationMillis - 6_000), Math.max(0, durationMillis - 2_000), durationMillis, EndingType.HARD, 0, MusicalTimeline.empty());
    }

    public AutoMixTrackAnalysis withProfile(AutoMixProfile replacement) {
        return new AutoMixTrackAnalysis(replacement, firstSoundMillis, firstStrongMillis, fadeOutStartMillis, lastStrongMillis, lastSoundMillis, endingType, endingConfidence, timeline);
    }

    public AutoMixTrackAnalysis withTimeline(MusicalTimeline replacement) {
        return new AutoMixTrackAnalysis(profile, firstSoundMillis, firstStrongMillis, fadeOutStartMillis, lastStrongMillis, lastSoundMillis, endingType, endingConfidence, replacement);
    }

    public enum EndingType {
        HARD, NATURAL_FADE, TRAILING_SILENCE
    }
}
