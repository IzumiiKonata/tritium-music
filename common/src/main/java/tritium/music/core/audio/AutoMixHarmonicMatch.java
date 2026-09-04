package tritium.music.core.audio;

public record AutoMixHarmonicMatch(int pitchShiftSemitones, double similarity, double improvement) {
    public static AutoMixHarmonicMatch between(AutoMixTrackAnalysis outgoing, AutoMixTrackAnalysis incoming) {
        return between(outgoing, incoming, Math.max(outgoing.timeline().startMillis(), outgoing.lastSoundMillis() - 8_000), incoming.firstSoundMillis(), 8_000);
    }

    public static AutoMixHarmonicMatch between(AutoMixTrackAnalysis outgoing, AutoMixTrackAnalysis incoming, long outgoingMillis, long incomingMillis, long durationMillis) {
        double[] outgoingChroma = chroma(outgoing.timeline(), outgoingMillis, outgoingMillis + durationMillis);
        double[] incomingChroma = chroma(incoming.timeline(), incomingMillis, incomingMillis + durationMillis);
        double unshifted = similarity(outgoingChroma, incomingChroma);
        double best = unshifted;
        int bestShift = 0;
        for (int shift = -3; shift <= 3; shift++) {
            double candidate = similarity(rotate(outgoingChroma, shift), incomingChroma) - Math.abs(shift) * 0.015;
            if (candidate > best) {
                best = candidate;
                bestShift = shift;
            }
        }
        double improvement = best - unshifted;
        if (best < 0.72 || improvement < 0.1) {
            return new AutoMixHarmonicMatch(0, unshifted, 0);
        }
        return new AutoMixHarmonicMatch(bestShift, best, improvement);
    }

    private static double[] chroma(MusicalTimeline timeline, long startMillis, long endMillis) {
        double[] result = new double[12];
        if (!timeline.isUsable()) {
            return result;
        }
        for (MusicalTimeline.Frame frame : timeline.frames()) {
            if (frame.timeMillis() < startMillis || frame.timeMillis() > endMillis || frame.harmonicClarity() < 0.28) {
                continue;
            }
            double weight = Math.max(0.1, frame.harmonicClarity()) * Math.pow(10, Math.max(-36, frame.loudnessDb()) / 20);
            for (int pitchClass = 0; pitchClass < result.length; pitchClass++) {
                result[pitchClass] += frame.chroma()[pitchClass] * weight;
            }
        }
        return result;
    }

    private static double[] rotate(double[] source, int semitones) {
        double[] result = new double[source.length];
        for (int pitchClass = 0; pitchClass < source.length; pitchClass++) {
            result[Math.floorMod(pitchClass + semitones, source.length)] = source[pitchClass];
        }
        return result;
    }

    private static double similarity(double[] left, double[] right) {
        double dot = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftEnergy += left[i] * left[i];
            rightEnergy += right[i] * right[i];
        }
        if (leftEnergy < 1.0e-9 || rightEnergy < 1.0e-9) {
            return 0;
        }
        return Math.max(0, Math.min(1, dot / Math.sqrt(leftEnergy * rightEnergy)));
    }
}
