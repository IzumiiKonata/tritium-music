package tritium.music.core.audio;

import java.util.List;

public record AutoMixStyleProfile(Style style, Intent intent, long maxIntroSkipMillis, long maxOverlapMillis,
                                  boolean allowTempoAndPitch, double eqStrength, double acceptanceScore,
                                  double outgoingProtection, double incomingProtection) {

    public static AutoMixStyleProfile forPair(AutoMixTrackAnalysis outgoing, AutoMixTrackAnalysis incoming) {
        Style outgoingStyle = classify(outgoing);
        Style incomingStyle = classify(incoming);
        double outgoingProtection = endingProtection(outgoing);
        double incomingProtection = openingProtection(incoming);
        if (outgoingProtection >= 0.67 || incomingProtection >= 0.67 || outgoingStyle == Style.DYNAMIC || incomingStyle == Style.DYNAMIC) {
            return new AutoMixStyleProfile(Style.DYNAMIC, Intent.CONTENT_HANDOFF, 900, 3_200, false, 0.38, 0.68, outgoingProtection, incomingProtection);
        }
        if (outgoingStyle == Style.CLUB && incomingStyle == Style.CLUB) {
            return new AutoMixStyleProfile(Style.CLUB, Intent.BEAT_MIX, 12_000, 18_000, true, 1, 0.59, outgoingProtection, incomingProtection);
        }
        if (outgoingStyle == Style.RHYTHMIC_POP && incomingStyle == Style.RHYTHMIC_POP) {
            return new AutoMixStyleProfile(Style.RHYTHMIC_POP, Intent.PHRASE_BLEND, 4_000, 9_000, true, 0.78, 0.62, outgoingProtection, incomingProtection);
        }
        if (outgoingStyle == Style.ATMOSPHERIC || incomingStyle == Style.ATMOSPHERIC) {
            return new AutoMixStyleProfile(Style.ATMOSPHERIC, Intent.FADE_HANDOFF, 1_500, 6_000, false, 0.45, 0.66, outgoingProtection, incomingProtection);
        }
        if (outgoingStyle == Style.ACOUSTIC || incomingStyle == Style.ACOUSTIC || outgoingStyle == Style.VOCAL_MELODIC || incomingStyle == Style.VOCAL_MELODIC) {
            return new AutoMixStyleProfile(Style.VOCAL_MELODIC, Intent.CONTENT_HANDOFF, 750, 3_500, false, 0.34, 0.68, outgoingProtection, incomingProtection);
        }
        return new AutoMixStyleProfile(Style.RHYTHMIC_POP, Intent.PHRASE_BLEND, 2_500, 7_000, true, 0.68, 0.64, outgoingProtection, incomingProtection);
    }

    public static Style classify(AutoMixTrackAnalysis analysis) {
        MusicalTimeline timeline = analysis.timeline();
        if (!timeline.isUsable()) {
            return Style.UNKNOWN;
        }
        double density = average(timeline.frames().stream().mapToDouble(MusicalTimeline.Frame::melodyDensity).toArray());
        double clarity = average(timeline.frames().stream().mapToDouble(MusicalTimeline.Frame::harmonicClarity).toArray());
        double movement = average(timeline.frames().stream().mapToDouble(frame -> Math.abs(frame.energyTrend())).toArray());
        double accentContrast = accentContrast(timeline.beatAccents());
        double rhythm = analysis.profile().beatConfidence() * 0.75 + accentContrast * 0.25;
        double endingProtection = endingProtection(analysis);
        if (endingProtection >= 0.72 || movement >= 0.42) {
            return Style.DYNAMIC;
        }
        if (rhythm >= 0.80 && density <= 0.20 && accentContrast >= 0.42) {
            return Style.CLUB;
        }
        if (rhythm >= 0.68 && density <= 0.42) {
            return Style.RHYTHMIC_POP;
        }
        if (density >= 0.40 && rhythm >= 0.48) {
            return Style.VOCAL_MELODIC;
        }
        if (clarity >= 0.58 && rhythm < 0.62) {
            return Style.ACOUSTIC;
        }
        if (rhythm < 0.48 && movement < 0.28) {
            return Style.ATMOSPHERIC;
        }
        return Style.VOCAL_MELODIC;
    }

    private static double openingProtection(AutoMixTrackAnalysis analysis) {
        long from = analysis.firstSoundMillis();
        long to = Math.min(from + 6_000, analysis.timeline().startMillis() + analysis.timeline().durationMillis());
        return analysis.timeline().contentSalience(from, to);
    }

    private static double endingProtection(AutoMixTrackAnalysis analysis) {
        long to = analysis.lastSoundMillis();
        long from = Math.max(analysis.timeline().startMillis(), to - 8_000);
        double salience = analysis.timeline().contentSalience(from, to);
        double rising = analysis.timeline().positiveEnergyTrend(from, to);
        return clamp(salience * 0.72 + rising * 0.28);
    }

    private static double accentContrast(List<MusicalTimeline.BeatAccent> accents) {
        if (accents.size() < 4) {
            return 0;
        }
        double mean = accents.stream().mapToDouble(MusicalTimeline.BeatAccent::strength).average().orElse(0);
        double variance = accents.stream().mapToDouble(accent -> {
            double delta = accent.strength() - mean;
            return delta * delta;
        }).average().orElse(0);
        return clamp(Math.sqrt(variance) * 2.4);
    }

    private static double average(double[] values) {
        return values.length == 0 ? 0 : java.util.Arrays.stream(values).average().orElse(0);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public enum Style {
        CLUB, RHYTHMIC_POP, VOCAL_MELODIC, ACOUSTIC, ATMOSPHERIC, DYNAMIC, UNKNOWN
    }

    public enum Intent {
        BEAT_MIX, PHRASE_BLEND, FADE_HANDOFF, CONTENT_HANDOFF
    }
}
