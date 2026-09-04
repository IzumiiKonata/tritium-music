package tritium.music.core.audio;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class BasicPitchAnalyzer {
    private static final int SAMPLE_RATE = 22_050;
    private static final int WINDOW_SAMPLES = 43_844;
    private static final int OVERLAP_SAMPLES = 7_680;
    private static final int WINDOW_HOP = WINDOW_SAMPLES - OVERLAP_SAMPLES;
    private static final int OUTPUT_FRAMES = 172;
    private static final int OVERLAP_FRAMES = 30;
    private static final int TRIM_FRAMES = OVERLAP_FRAMES / 2;
    private static final int VALID_FRAMES = OUTPUT_FRAMES - OVERLAP_FRAMES;
    private static final int NOTE_BINS = 88;
    private static final double FRAMES_PER_SECOND = 86;
    private static final int FEATURE_MILLIS = 500;
    private static final String MODEL_PATH = "/assets/tritium-music/automix/basic_pitch.onnx";

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
        java.util.Arrays.sort(sorted);
        double reference = sorted.length == 0 ? 1 : Math.max(1.0e-6, sorted[(int) Math.floor((sorted.length - 1) * 0.72)]);
        List<MusicalTimeline.BeatAccent> result = new ArrayList<>(beats.size());
        for (int beat = 0; beat < beats.size(); beat++) {
            result.add(new MusicalTimeline.BeatAccent(beats.get(beat), clamp(raw[beat] / reference)));
        }
        return result;
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
        for (int i = 0; i < source.size(); i++) {
            MusicalTimeline.Frame frame = source.get(i);
            MusicalTimeline.Frame previous = source.get(Math.max(0, i - 2));
            MusicalTimeline.Frame next = source.get(Math.min(source.size() - 1, i + 2));
            double trend = clampSigned((next.loudnessDb() - previous.loudnessDb()) / 12);
            double harmonicChange = 1 - cosine(previous.chroma(), next.chroma());
            double energyChange = Math.min(1, Math.abs(next.loudnessDb() - previous.loudnessDb()) / 14);
            double pitchChange = Math.min(1, Math.abs(next.melodyPitch() - previous.melodyPitch()) / 18);
            double novelty = clamp(harmonicChange * 0.5 + energyChange * 0.3 + pitchChange * 0.2);
            result.add(new MusicalTimeline.Frame(frame.timeMillis(), frame.loudnessDb(), trend, frame.melodyPitch(), frame.melodyDensity(), frame.harmonicClarity(), novelty, frame.chroma()));
        }
        return result;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftEnergy = 0;
        double rightEnergy = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftEnergy += left[i] * left[i];
            rightEnergy += right[i] * right[i];
        }
        return leftEnergy < 1.0e-9 || rightEnergy < 1.0e-9 ? 0 : dot / Math.sqrt(leftEnergy * rightEnergy);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    MusicalTimeline analyze(float[] audio, long timelineOffsetMillis, BeatThisTempoAnalyzer.BeatGrid beatGrid) throws OrtException {
        int featureCount = Math.max(1, (int) Math.ceil(audio.length * 1_000.0 / SAMPLE_RATE / FEATURE_MILLIS));
        Accumulator[] accumulators = new Accumulator[featureCount];
        for (int i = 0; i < featureCount; i++) {
            accumulators[i] = new Accumulator();
        }
        Models.INSTANCE.analyze(audio, accumulators);
        double[] loudness = energy(audio, featureCount);
        List<MusicalTimeline.Frame> frames = new ArrayList<>(featureCount);
        for (int i = 0; i < featureCount; i++) {
            Accumulator accumulator = accumulators[i];
            double chromaSum = 0;
            double chromaMaximum = 0;
            for (double value : accumulator.chroma) {
                chromaSum += value;
                chromaMaximum = Math.max(chromaMaximum, value);
            }
            double[] chroma = accumulator.chroma.clone();
            if (chromaSum > 1.0e-9) {
                for (int pitchClass = 0; pitchClass < chroma.length; pitchClass++) {
                    chroma[pitchClass] /= chromaSum;
                }
            }
            double pitch = accumulator.pitchWeight == 0 ? 0 : accumulator.pitchSum / accumulator.pitchWeight;
            double density = clamp(accumulator.density / Math.max(1, accumulator.frames) / 5);
            double clarity = chromaSum == 0 ? 0 : clamp(chromaMaximum / chromaSum * 3);
            frames.add(new MusicalTimeline.Frame(timelineOffsetMillis + (long) i * FEATURE_MILLIS, loudness[i], 0, pitch, density, clarity, 0, chroma));
        }
        frames = addDynamics(frames);
        long durationMillis = Math.round(audio.length * 1_000.0 / SAMPLE_RATE);
        return new MusicalTimeline(timelineOffsetMillis, durationMillis, frames, beatGrid.beatTimesMillis(), beatGrid.downbeatTimesMillis(), beatAccents(audio, timelineOffsetMillis, beatGrid.beatTimesMillis()));
    }

    private static final class Accumulator {
        private final double[] chroma = new double[12];
        private double pitchSum;
        private double pitchWeight;
        private double density;
        private int frames;
    }

    private record Models(OrtEnvironment environment, OrtSession session) {
        private static final Models INSTANCE = create();

        private static Models create() {
            try {
                OrtEnvironment environment = OrtEnvironment.getEnvironment("Tritium AutoMix");
                OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                options.setInterOpNumThreads(1);
                options.setIntraOpNumThreads(1);
                OrtSession session = environment.createSession(readResource(MODEL_PATH), options);
                options.close();
                return new Models(environment, session);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to initialize Basic Pitch", e);
            }
        }

        private static float[] tensorValues(OrtSession.Result output, String name) {
            OnnxTensor tensor = (OnnxTensor) output.get(name).orElseThrow(() -> new IllegalStateException("Missing " + name + " output"));
            float[] values = new float[Math.toIntExact(tensor.getInfo().getNumElements())];
            tensor.getFloatBuffer().get(values);
            return values;
        }

        private static byte[] readResource(String path) throws IOException {
            try (InputStream input = BasicPitchAnalyzer.class.getResourceAsStream(path)) {
                if (input == null) {
                    throw new IOException("Missing resource " + path);
                }
                return input.readAllBytes();
            }
        }

        private synchronized void analyze(float[] audio, Accumulator[] accumulators) throws OrtException {
            int windows = Math.max(1, (int) Math.ceil((audio.length + OVERLAP_SAMPLES - WINDOW_SAMPLES) / (double) WINDOW_HOP) + 1);
            float[] window = new float[WINDOW_SAMPLES];
            for (int windowIndex = 0; windowIndex < windows; windowIndex++) {
                Arrays.fill(window, 0);
                int sourceStart = windowIndex * WINDOW_HOP - OVERLAP_SAMPLES / 2;
                int copyStart = Math.max(0, sourceStart);
                int copyEnd = Math.min(audio.length, sourceStart + WINDOW_SAMPLES);
                if (copyEnd > copyStart) {
                    System.arraycopy(audio, copyStart, window, copyStart - sourceStart, copyEnd - copyStart);
                }
                try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(window), new long[]{1, WINDOW_SAMPLES, 1}); OrtSession.Result output = session.run(Map.of("serving_default_input_2:0", input))) {
                    float[] note = tensorValues(output, "StatefulPartitionedCall:1");
                    float[] onset = tensorValues(output, "StatefulPartitionedCall:2");
                    int modelFrames = note.length / NOTE_BINS;
                    int validEnd = Math.min(modelFrames - TRIM_FRAMES, TRIM_FRAMES + VALID_FRAMES);
                    for (int frame = TRIM_FRAMES; frame < validEnd; frame++) {
                        int globalFrame = windowIndex * VALID_FRAMES + frame - TRIM_FRAMES;
                        int feature = (int) Math.floor(globalFrame / FRAMES_PER_SECOND * 1_000 / FEATURE_MILLIS);
                        if (feature >= accumulators.length) {
                            break;
                        }
                        Accumulator accumulator = accumulators[feature];
                        accumulator.frames++;
                        for (int pitch = 0; pitch < NOTE_BINS; pitch++) {
                            int index = frame * NOTE_BINS + pitch;
                            double activation = Math.max(note[index], onset[index] * 0.72);
                            if (activation < 0.12) {
                                continue;
                            }
                            double weight = activation * activation;
                            accumulator.chroma[pitch % 12] += weight;
                            accumulator.pitchSum += pitch * weight;
                            accumulator.pitchWeight += weight;
                            accumulator.density += activation;
                        }
                    }
                }
            }
        }
    }
}
