package tritium.music.core.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TransitionCandidateSearch {
    private TransitionCandidateSearch() {
    }

    public static Candidate find(AutoMixTrackAnalysis current, AutoMixTrackAnalysis next) {
        return find(current, next, AutoMixStyleProfile.forPair(current, next));
    }

    public static Candidate find(AutoMixTrackAnalysis current, AutoMixTrackAnalysis next, AutoMixStyleProfile guidance) {
        MusicalTimeline outgoing = current.timeline();
        MusicalTimeline incoming = next.timeline();
        if (!outgoing.isUsable() || !incoming.isUsable() || !current.profile().downbeatAware() || !next.profile().downbeatAware()) {
            return null;
        }
        long lastSound = current.lastSoundMillis();
        List<Long> exits = outgoing.beatsBetween(Math.max(outgoing.startMillis(), lastSound - guidance.maxOverlapMillis()), Math.max(outgoing.startMillis(), lastSound - 1_200));
        long entryEnd = Math.min(next.firstSoundMillis() + guidance.maxIntroSkipMillis(), incoming.startMillis() + incoming.durationMillis());
        List<Long> entries = incoming.beatsBetween(Math.max(incoming.startMillis(), next.firstSoundMillis()), entryEnd);
        if (exits.isEmpty() || entries.isEmpty()) {
            return null;
        }
        double rate = current.profile().beatMatchRateTo(next.profile(), AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);
        boolean tempoCompatible = current.profile().isTempoCompatible(next.profile(), AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);
        List<Candidate> candidates = new ArrayList<>();
        for (long exit : exits) {
            for (long entry : entries) {
                candidates.add(score(outgoing, incoming, exit, entry, lastSound, rate, tempoCompatible, current, next, guidance));
            }
        }
        Candidate best = candidates.stream().max(Comparator.comparingDouble(Candidate::score)).orElse(null);
        return best != null && best.score() >= guidance.acceptanceScore() ? best : null;
    }

    private static Candidate score(MusicalTimeline outgoing, MusicalTimeline incoming, long exit, long entry, long lastSound, double rate, boolean tempoCompatible, AutoMixTrackAnalysis outgoingAnalysis, AutoMixTrackAnalysis incomingAnalysis, AutoMixStyleProfile guidance) {
        AutoMixProfile outgoingProfile = outgoingAnalysis.profile();
        AutoMixProfile incomingProfile = incomingAnalysis.profile();
        double tempoChange = Math.abs(rate - 1);
        double rhythm = tempoCompatible ? clamp(1 - tempoChange / AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE) * 0.55 + Math.min(outgoingProfile.beatConfidence(), incomingProfile.beatConfidence()) * 0.45 : 0.22;
        long availableIncoming = incoming.startMillis() + incoming.durationMillis() - entry;
        long comparisonMillis = Math.max(1_500, Math.min(8_000, Math.min(lastSound - exit, availableIncoming)));
        double[] outgoingChroma = new double[12];
        double[] incomingChroma = new double[12];
        double energy = 0;
        double melody = 0;
        double structure = 0;
        double clarity = 0;
        int samples = 0;
        for (long elapsed = 0; elapsed <= comparisonMillis; elapsed += 500) {
            MusicalTimeline.Frame outgoingFrame = outgoing.frameAt(exit + elapsed);
            MusicalTimeline.Frame incomingFrame = incoming.frameAt(entry + elapsed);
            add(outgoingChroma, outgoingFrame.chroma());
            add(incomingChroma, incomingFrame.chroma());
            double loudnessMatch = clamp(1 - Math.abs(outgoingFrame.loudnessDb() - incomingFrame.loudnessDb()) / 18);
            double energyDirection = clamp((incomingFrame.energyTrend() - outgoingFrame.energyTrend() + 1) / 2);
            energy += loudnessMatch * 0.65 + energyDirection * 0.35;
            melody += clamp(1 - Math.sqrt(outgoingFrame.melodyDensity() * incomingFrame.melodyDensity()));
            structure += clamp((incomingFrame.energyTrend() - outgoingFrame.energyTrend() + 1.2) / 2.4);
            clarity += Math.min(outgoingFrame.harmonicClarity(), incomingFrame.harmonicClarity());
            samples++;
        }
        energy /= samples;
        melody /= samples;
        structure /= samples;
        clarity /= samples;
        int pitchShift = bestPitchShift(outgoingChroma, incomingChroma, clarity);
        double harmony = chromaSimilarity(rotate(outgoingChroma, pitchShift), incomingChroma) - Math.abs(pitchShift) * 0.012;
        double meter = meterScore(outgoing, incoming, exit, entry, outgoingProfile, incomingProfile);
        double boundary = (outgoing.boundarySafety(exit) + incoming.boundarySafety(entry)) / 2;
        double overlapFraction = clamp((lastSound - exit) / (double) guidance.maxOverlapMillis());
        double introSkipFraction = guidance.maxIntroSkipMillis() <= 0 ? 0 : clamp((entry - incomingAnalysis.firstSoundMillis()) / (double) guidance.maxIntroSkipMillis());
        double outgoingLoss = outgoing.contentSalience(exit, lastSound) * Math.sqrt(overlapFraction);
        double incomingLoss = entry <= incomingAnalysis.firstSoundMillis() ? 0 : incoming.contentSalience(incomingAnalysis.firstSoundMillis(), entry) * Math.sqrt(introSkipFraction);
        double outgoingPreservation = clamp(1 - outgoingLoss);
        double incomingPreservation = clamp(1 - incomingLoss);
        double preservation = outgoingPreservation * 0.58 + incomingPreservation * 0.42;
        double score = rhythm * 0.15 + meter * 0.16 + harmony * 0.17 + energy * 0.13 + melody * 0.10 + boundary * 0.11 + structure * 0.05 + preservation * 0.13;
        double selectedRate = guidance.allowTempoAndPitch() ? rate : 1;
        int selectedPitchShift = guidance.allowTempoAndPitch() ? pitchShift : 0;
        return new Candidate(exit, entry, score, rhythm, meter, harmony, energy, melody, boundary, outgoingPreservation, incomingPreservation, selectedRate, selectedPitchShift, guidance.style(), guidance.intent(), guidance.eqStrength());
    }

    private static double meterScore(MusicalTimeline outgoing, MusicalTimeline incoming, long exit, long entry, AutoMixProfile outgoingProfile, AutoMixProfile incomingProfile) {
        int outgoingMeter = meter(outgoingProfile);
        int incomingMeter = meter(incomingProfile);
        int samples = Math.max(4, Math.min(8, Math.min(outgoing.beats().size(), incoming.beats().size())));
        double[] outgoingAccents = new double[samples];
        double[] incomingAccents = new double[samples];
        for (int beat = 0; beat < samples; beat++) {
            outgoingAccents[beat] = outgoing.beatAccentAt(Math.round(exit + beat * outgoingProfile.beatIntervalMillis()));
            incomingAccents[beat] = incoming.beatAccentAt(Math.round(entry + beat * incomingProfile.beatIntervalMillis()));
        }
        double accentMatch = centeredSimilarity(outgoingAccents, incomingAccents);
        int outgoingPosition = outgoing.barPositionAt(exit, outgoingMeter);
        int incomingPosition = incoming.barPositionAt(entry, incomingMeter);
        double phaseAgreement = outgoingMeter == incomingMeter && outgoingPosition >= 0 && outgoingPosition == incomingPosition ? 1 : 0.25;
        double startStrength = (outgoingAccents[0] + incomingAccents[0]) / 2;
        return clamp(accentMatch * 0.68 + phaseAgreement * 0.20 + startStrength * 0.12);
    }

    private static int meter(AutoMixProfile profile) {
        if (profile.downbeatIntervalMillis() <= 0 || profile.beatIntervalMillis() <= 0) {
            return 4;
        }
        return Math.max(3, Math.min(4, (int) Math.round(profile.downbeatIntervalMillis() / profile.beatIntervalMillis())));
    }

    private static double centeredSimilarity(double[] left, double[] right) {
        double leftMean = java.util.Arrays.stream(left).average().orElse(0);
        double rightMean = java.util.Arrays.stream(right).average().orElse(0);
        double dot = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            double leftValue = left[i] - leftMean;
            double rightValue = right[i] - rightMean;
            dot += leftValue * rightValue;
            leftEnergy += leftValue * leftValue;
            rightEnergy += rightValue * rightValue;
        }
        if (leftEnergy < 1.0e-8 || rightEnergy < 1.0e-8) {
            return 0.5;
        }
        return clamp((dot / Math.sqrt(leftEnergy * rightEnergy) + 1) / 2);
    }

    private static int bestPitchShift(double[] outgoing, double[] incoming, double clarity) {
        double unshifted = chromaSimilarity(outgoing, incoming);
        double best = unshifted;
        int bestShift = 0;
        for (int shift = -4; shift <= 4; shift++) {
            double similarity = chromaSimilarity(rotate(outgoing, shift), incoming) - Math.abs(shift) * 0.008;
            if (similarity > best) {
                best = similarity;
                bestShift = shift;
            }
        }
        return clarity >= 0.28 && best - unshifted >= 0.075 ? bestShift : 0;
    }

    private static double[] rotate(double[] chroma, int semitones) {
        double[] result = new double[chroma.length];
        for (int pitchClass = 0; pitchClass < chroma.length; pitchClass++) {
            result[Math.floorMod(pitchClass + semitones, chroma.length)] = chroma[pitchClass];
        }
        return result;
    }

    private static void add(double[] target, double[] values) {
        for (int i = 0; i < Math.min(target.length, values.length); i++) {
            target[i] += values[i];
        }
    }

    private static double chromaSimilarity(double[] left, double[] right) {
        double dot = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftEnergy += left[i] * left[i];
            rightEnergy += right[i] * right[i];
        }
        if (leftEnergy < 1.0e-8 || rightEnergy < 1.0e-8) {
            return 0.35;
        }
        return clamp(dot / Math.sqrt(leftEnergy * rightEnergy));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record Candidate(long outgoingMillis, long incomingMillis, double score, double rhythmScore,
                            double meterScore, double harmonyScore, double energyScore, double melodyScore,
                            double boundaryScore, double outgoingPreservation, double incomingPreservation,
                            double playbackRate, int pitchShiftSemitones, AutoMixStyleProfile.Style style,
                            AutoMixStyleProfile.Intent intent, double eqStrength) {
    }
}
