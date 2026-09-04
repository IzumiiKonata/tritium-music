package tritium.music.core.audio;

import java.util.Comparator;
import java.util.List;

public final class AutoMixTransitionSearch {
    private static final long MAXIMUM_INTRO_SKIP_MILLIS = 3_500;
    private static final long MAXIMUM_OVERLAP_MILLIS = 11_000;
    private static final long FULL_TAIL_PRESERVATION_MILLIS = 4_000;

    private AutoMixTransitionSearch() {
    }

    public static long incomingCue(AutoMixTrackAnalysis analysis) {
        MusicalTimeline timeline = analysis.timeline();
        AutoMixProfile profile = analysis.profile();
        long firstSound = analysis.firstSoundMillis();
        if (!timeline.isUsable() || !profile.downbeatAware() || profile.beatConfidence() < 0.72) {
            return firstSound;
        }
        long latest = Math.min(firstSound + MAXIMUM_INTRO_SKIP_MILLIS, timeline.startMillis() + timeline.durationMillis());
        List<Long> candidates = timeline.downbeatsBetween(firstSound, latest);
        if (candidates.isEmpty()) {
            return firstSound;
        }
        long first = candidates.getFirst();
        CueScore baseline = scoreIncomingCue(analysis, first);
        CueScore best = candidates.stream().map(candidate -> scoreIncomingCue(analysis, candidate)).max(Comparator.comparingDouble(CueScore::score)).orElse(baseline);
        return best.score() >= baseline.score() + 0.16 ? best.millis() : first;
    }

    public static Selection find(AutoMixTrackAnalysis outgoing, AutoMixTrackAnalysis incoming) {
        MusicalTimeline outgoingTimeline = outgoing.timeline();
        MusicalTimeline incomingTimeline = incoming.timeline();
        AutoMixProfile outgoingProfile = outgoing.profile();
        AutoMixProfile incomingProfile = incoming.profile();
        if (!outgoingTimeline.isUsable() || !incomingTimeline.isUsable() || !outgoingProfile.downbeatAware() || !incomingProfile.downbeatAware() || outgoingProfile.beatConfidence() < 0.72 || incomingProfile.beatConfidence() < 0.72 || !outgoingProfile.isTempoCompatible(incomingProfile, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE)) {
            return null;
        }
        long lastSound = outgoing.lastSoundMillis();
        long earliest = Math.max(outgoingTimeline.startMillis(), lastSound - MAXIMUM_OVERLAP_MILLIS);
        long latest = Math.max(earliest, lastSound - 2_500);
        List<Long> exits = outgoingTimeline.downbeatsBetween(earliest, latest);
        long entryEnd = Math.min(incoming.firstSoundMillis() + MAXIMUM_INTRO_SKIP_MILLIS, incomingTimeline.startMillis() + incomingTimeline.durationMillis());
        List<Long> entries = incomingTimeline.downbeatsBetween(incoming.firstSoundMillis(), entryEnd);
        if (exits.isEmpty() || entries.isEmpty()) {
            return null;
        }
        double rate = outgoingProfile.beatMatchRateTo(incomingProfile, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);
        Selection best = null;
        for (long exit : exits) {
            for (long entry : entries) {
                Selection candidate = score(outgoing, incoming, exit, entry, lastSound, rate);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }
        return best != null && best.score() >= 0.56 ? best : null;
    }

    private static CueScore scoreIncomingCue(AutoMixTrackAnalysis analysis, long cueMillis) {
        MusicalTimeline timeline = analysis.timeline();
        long firstSound = analysis.firstSoundMillis();
        double boundary = timeline.boundarySafety(cueMillis);
        double skippedContent = cueMillis <= firstSound ? 0 : timeline.contentSalience(firstSound, cueMillis);
        MusicalTimeline.Frame at = timeline.frameAt(cueMillis);
        MusicalTimeline.Frame after = timeline.frameAt(cueMillis + 1_500);
        double energyLift = clamp((after.loudnessDb() - at.loudnessDb() + 8) / 16);
        double openingSpace = clamp(1 - at.melodyDensity());
        double early = 1 - clamp((cueMillis - firstSound) / (double) MAXIMUM_INTRO_SKIP_MILLIS);
        double score = boundary * 0.24 + (1 - skippedContent) * 0.38 + energyLift * 0.12 + openingSpace * 0.10 + early * 0.16;
        return new CueScore(cueMillis, score);
    }

    private static Selection score(AutoMixTrackAnalysis outgoing, AutoMixTrackAnalysis incoming, long exitMillis, long entryMillis, long lastSound, double rate) {
        long trackOverlapMillis = lastSound - exitMillis;
        double beats = trackOverlapMillis / outgoing.profile().beatIntervalMillis();
        long incomingStructureMillis = nextStructureBoundary(incoming.timeline(), entryMillis) - entryMillis;
        double structureLength = ratioScore(trackOverlapMillis, incomingStructureMillis);
        double trajectory = trajectoryMatch(outgoing.timeline(), incoming.timeline(), exitMillis, entryMillis, trackOverlapMillis, rate);
        double structure = structureLength * 0.62 + trajectory * 0.38;
        double boundary = outgoing.timeline().boundarySafety(exitMillis) * 0.62 + incoming.timeline().boundarySafety(entryMillis) * 0.38;
        double vocalCollision = vocalCollision(outgoing.timeline(), incoming.timeline(), exitMillis, entryMillis, trackOverlapMillis, rate);
        AutoMixHarmonicMatch harmonic = AutoMixHarmonicMatch.between(outgoing, incoming, exitMillis, entryMillis, trackOverlapMillis);
        double tempo = clamp(1 - Math.abs(rate - 1) / AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);
        double outgoingDevelopment = outgoing.timeline().positiveEnergyTrend(exitMillis, lastSound);
        double incomingDevelopment = incoming.timeline().positiveEnergyTrend(entryMillis, entryMillis + Math.min(8_000, trackOverlapMillis));
        double energyArc = clamp((incomingDevelopment - outgoingDevelopment + 1) / 2);
        double tailPreservation = 1 - clamp((trackOverlapMillis - FULL_TAIL_PRESERVATION_MILLIS) / (double) (MAXIMUM_OVERLAP_MILLIS - FULL_TAIL_PRESERVATION_MILLIS));
        double score = structure * 0.19 + boundary * 0.21 + (1 - vocalCollision) * 0.20 + harmonic.similarity() * 0.12 + tempo * 0.08 + energyArc * 0.06 + tailPreservation * 0.14;
        return new Selection(exitMillis, entryMillis, trackOverlapMillis, Math.max(1, (int) Math.round(beats)), score, structure, boundary, vocalCollision, harmonic.similarity(), energyArc);
    }

    private static double vocalCollision(MusicalTimeline outgoing, MusicalTimeline incoming, long exitMillis, long entryMillis, long trackOverlapMillis, double rate) {
        double total = 0;
        int samples = 0;
        long wallDuration = Math.max(1, Math.round(trackOverlapMillis / Math.max(0.01, rate)));
        for (long elapsed = 0; elapsed <= wallDuration; elapsed += 500) {
            MusicalTimeline.Frame left = outgoing.frameAt(exitMillis + Math.round(elapsed * rate));
            MusicalTimeline.Frame right = incoming.frameAt(entryMillis + elapsed);
            total += Math.sqrt(clamp(left.melodyDensity()) * clamp(right.melodyDensity()));
            samples++;
        }
        return samples == 0 ? 1 : clamp(total / samples);
    }

    private static long nextStructureBoundary(MusicalTimeline timeline, long startMillis) {
        long timelineEnd = timeline.startMillis() + timeline.durationMillis();
        List<Long> candidates = timeline.downbeatsBetween(startMillis + 1_000, Math.min(timelineEnd, startMillis + MAXIMUM_OVERLAP_MILLIS));
        if (candidates.isEmpty()) {
            return Math.min(timelineEnd, startMillis + MAXIMUM_OVERLAP_MILLIS);
        }
        long best = candidates.getLast();
        double bestScore = -1;
        for (long candidate : candidates) {
            MusicalTimeline.Frame frame = timeline.frameAt(candidate);
            double distance = (candidate - startMillis) / (double) MAXIMUM_OVERLAP_MILLIS;
            double score = timeline.boundarySafety(candidate) * 0.68 + clamp(frame.novelty()) * 0.24 + distance * 0.08;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static double ratioScore(long leftMillis, long rightMillis) {
        if (leftMillis <= 0 || rightMillis <= 0) {
            return 0;
        }
        return Math.exp(-Math.abs(Math.log(leftMillis / (double) rightMillis)) * 1.4);
    }

    private static double trajectoryMatch(MusicalTimeline outgoing, MusicalTimeline incoming, long exitMillis, long entryMillis, long trackOverlapMillis, double rate) {
        long wallDuration = Math.max(1, Math.round(trackOverlapMillis / Math.max(0.01, rate)));
        double score = 0;
        int samples = 0;
        for (int step = 0; step <= 8; step++) {
            long elapsed = wallDuration * step / 8;
            MusicalTimeline.Frame left = outgoing.frameAt(exitMillis + Math.round(elapsed * rate));
            MusicalTimeline.Frame right = incoming.frameAt(entryMillis + elapsed);
            double direction = clamp((right.energyTrend() - left.energyTrend() + 1) / 2);
            double densitySpace = clamp(1 - Math.sqrt(clamp(left.melodyDensity()) * clamp(right.melodyDensity())));
            score += direction * 0.55 + densitySpace * 0.45;
            samples++;
        }
        return score / samples;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record CueScore(long millis, double score) {
    }

    public record Selection(long outgoingMillis, long incomingMillis, long trackOverlapMillis, int beats, double score,
                            double structureScore, double boundaryScore, double vocalCollision, double harmonicScore,
                            double energyScore) {
    }
}
