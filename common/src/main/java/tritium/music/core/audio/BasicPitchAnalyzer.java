package tritium.music.core.audio;

import tritium.music.repackage.com.jsyn.util.FourierMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BasicPitchAnalyzer {
    private static final int SAMPLE_RATE = 22_050;
    private static final int FEATURE_MILLIS = 500;
    private static final int FFT_SIZE = 4_096;
    private static final int FFT_HOP = 2_048;
    private static final double MINIMUM_FREQUENCY = 55;
    private static final double MAXIMUM_FREQUENCY = 2_200;

    private static void accumulateSpectrum(Accumulator accumulator, float[] real, float[] imaginary) {
        double maximum = 0;
        for (int bin = minimumBin(); bin <= maximumBin(); bin++) {
            maximum = Math.max(maximum, magnitude(real, imaginary, bin));
        }
        double threshold = maximum * 0.08;
        for (int bin = minimumBin(); bin <= maximumBin(); bin++) {
            double magnitude = magnitude(real, imaginary, bin);
            if (magnitude < threshold || magnitude <= magnitude(real, imaginary, bin - 1) || magnitude < magnitude(real, imaginary, bin + 1)) {
                continue;
            }
            double frequency = bin * SAMPLE_RATE / (double) FFT_SIZE;
            double midi = 69 + 12 * Math.log(frequency / 440) / Math.log(2);
            int nearestMidi = (int) Math.round(midi);
            double tuningWeight = Math.exp(-Math.pow(midi - nearestMidi, 2) / 0.08);
            double weight = magnitude * magnitude * tuningWeight;
            accumulator.chroma[Math.floorMod(nearestMidi, 12)] += weight;
            accumulator.pitchSum += nearestMidi * weight;
            accumulator.weight += weight;
            accumulator.activeBins++;
        }
        accumulator.windows++;
    }

    private static int minimumBin() {
        return Math.max(2, (int) Math.ceil(MINIMUM_FREQUENCY * FFT_SIZE / SAMPLE_RATE));
    }

    private static int maximumBin() {
        return Math.min(FFT_SIZE / 2 - 2, (int) Math.floor(MAXIMUM_FREQUENCY * FFT_SIZE / SAMPLE_RATE));
    }

    private static double magnitude(float[] real, float[] imaginary, int bin) {
        return Math.hypot(real[bin], imaginary[bin]);
    }

    private static double[] energy(float[] audio, int featureCount) {
        double[] result = new double[featureCount];
        int samplesPerFeature = SAMPLE_RATE * FEATURE_MILLIS / 1_000;
        for (int feature = 0; feature < featureCount; feature++) {
            int start = feature * samplesPerFeature;
            int end = Math.min(audio.length, start + samplesPerFeature);
            double sum = 0;
            for (int sample = start; sample < end; sample++) {
                sum += audio[sample] * audio[sample];
            }
            result[feature] = end == start ? -80 : Math.max(-80, 10 * Math.log10(sum / (end - start) + 1.0e-8));
        }
        return result;
    }

    private static List<MusicalTimeline.Frame> addDynamics(List<MusicalTimeline.Frame> source) {
        List<MusicalTimeline.Frame> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            MusicalTimeline.Frame frame = source.get(index);
            MusicalTimeline.Frame previous = source.get(Math.max(0, index - 2));
            MusicalTimeline.Frame next = source.get(Math.min(source.size() - 1, index + 2));
            double trend = clampSigned((next.loudnessDb() - previous.loudnessDb()) / 12);
            double harmonicChange = 1 - cosine(previous.chroma(), next.chroma());
            double energyChange = Math.min(1, Math.abs(next.loudnessDb() - previous.loudnessDb()) / 14);
            double pitchChange = Math.min(1, Math.abs(next.melodyPitch() - previous.melodyPitch()) / 18);
            double novelty = clamp(harmonicChange * 0.5 + energyChange * 0.3 + pitchChange * 0.2);
            result.add(new MusicalTimeline.Frame(frame.timeMillis(), frame.loudnessDb(), trend, frame.melodyPitch(), frame.melodyDensity(), frame.harmonicClarity(), novelty, frame.chroma()));
        }
        return result;
    }

    private static List<MusicalTimeline.BeatAccent> beatAccents(float[] audio, long timelineOffsetMillis, List<Long> beats) {
        double[] raw = new double[beats.size()];
        double[] sorted = new double[beats.size()];
        for (int beat = 0; beat < beats.size(); beat++) {
            int center = (int) Math.round((beats.get(beat) - timelineOffsetMillis) * SAMPLE_RATE / 1_000.0);
            int start = Math.max(1, center - SAMPLE_RATE / 100);
            int end = Math.min(audio.length, center + SAMPLE_RATE * 9 / 100);
            double energy = 0;
            double transientEnergy = 0;
            for (int sample = start; sample < end; sample++) {
                energy += audio[sample] * audio[sample];
                double difference = audio[sample] - audio[sample - 1];
                transientEnergy += difference * difference;
            }
            int count = Math.max(1, end - start);
            raw[beat] = Math.sqrt(energy / count) + Math.sqrt(transientEnergy / count) * 2.4;
            sorted[beat] = raw[beat];
        }
        Arrays.sort(sorted);
        double reference = sorted.length == 0 ? 1 : Math.max(1.0e-6, sorted[(int) Math.floor((sorted.length - 1) * 0.72)]);
        List<MusicalTimeline.BeatAccent> result = new ArrayList<>(beats.size());
        for (int beat = 0; beat < beats.size(); beat++) {
            result.add(new MusicalTimeline.BeatAccent(beats.get(beat), clamp(raw[beat] / reference)));
        }
        return result;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            dot += left[index] * right[index];
            leftEnergy += left[index] * left[index];
            rightEnergy += right[index] * right[index];
        }
        return leftEnergy < 1.0e-9 || rightEnergy < 1.0e-9 ? 0 : dot / Math.sqrt(leftEnergy * rightEnergy);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    MusicalTimeline analyze(float[] audio, long timelineOffsetMillis, BeatThisTempoAnalyzer.BeatGrid beatGrid) {
        int featureCount = Math.max(1, (int) Math.ceil(audio.length * 1_000.0 / SAMPLE_RATE / FEATURE_MILLIS));
        Accumulator[] accumulators = new Accumulator[featureCount];
        for (int index = 0; index < featureCount; index++) {
            accumulators[index] = new Accumulator();
        }
        float[] real = new float[FFT_SIZE];
        float[] imaginary = new float[FFT_SIZE];
        for (int start = 0; start < audio.length; start += FFT_HOP) {
            Arrays.fill(real, 0);
            Arrays.fill(imaginary, 0);
            int copied = Math.min(FFT_SIZE, audio.length - start);
            for (int sample = 0; sample < copied; sample++) {
                double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * sample / (FFT_SIZE - 1));
                real[sample] = (float) (audio[start + sample] * window);
            }
            FourierMath.transform(1, FFT_SIZE, real, imaginary);
            int center = Math.min(audio.length - 1, start + copied / 2);
            int feature = Math.min(featureCount - 1, (int) (center * 1_000L / SAMPLE_RATE / FEATURE_MILLIS));
            accumulateSpectrum(accumulators[feature], real, imaginary);
        }
        double[] loudness = energy(audio, featureCount);
        List<MusicalTimeline.Frame> frames = new ArrayList<>(featureCount);
        for (int index = 0; index < featureCount; index++) {
            Accumulator accumulator = accumulators[index];
            double chromaSum = Arrays.stream(accumulator.chroma).sum();
            double chromaMaximum = Arrays.stream(accumulator.chroma).max().orElse(0);
            double[] chroma = accumulator.chroma.clone();
            if (chromaSum > 1.0e-9) {
                for (int pitchClass = 0; pitchClass < chroma.length; pitchClass++) {
                    chroma[pitchClass] /= chromaSum;
                }
            }
            double pitch = accumulator.weight == 0 ? 0 : accumulator.pitchSum / accumulator.weight;
            double density = accumulator.windows == 0 ? 0 : clamp(accumulator.activeBins / (accumulator.windows * 28.0));
            double clarity = chromaSum == 0 ? 0 : clamp(chromaMaximum / chromaSum * 3.2);
            frames.add(new MusicalTimeline.Frame(timelineOffsetMillis + (long) index * FEATURE_MILLIS, loudness[index], 0, pitch, density, clarity, 0, chroma));
        }
        frames = addDynamics(frames);
        long durationMillis = Math.round(audio.length * 1_000.0 / SAMPLE_RATE);
        return new MusicalTimeline(timelineOffsetMillis, durationMillis, frames, beatGrid.beatTimesMillis(), beatGrid.downbeatTimesMillis(), beatAccents(audio, timelineOffsetMillis, beatGrid.beatTimesMillis()));
    }

    private static final class Accumulator {
        private final double[] chroma = new double[12];
        private double pitchSum;
        private double weight;
        private int activeBins;
        private int windows;
    }
}
