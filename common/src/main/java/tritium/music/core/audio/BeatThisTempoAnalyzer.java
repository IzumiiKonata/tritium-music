package tritium.music.core.audio;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class BeatThisTempoAnalyzer {
    private static final int TARGET_RATE = 22_050;
    private static final int MEL_BANDS = 128;
    private static final int CHUNK_SIZE = 1_500;
    private static final int BORDER_SIZE = 6;
    private static final int STRIDE = CHUNK_SIZE - BORDER_SIZE * 2;
    private static final int RESAMPLE_PHASES = 2_048;
    private static final int RESAMPLE_TAPS = 48;
    private static final String MODEL_PATH = "/assets/tritium-music/automix/beat_this.onnx";
    private static final String MEL_MODEL_PATH = "/assets/tritium-music/automix/mel_spectrogram.onnx";

    private float[] samples = new float[TARGET_RATE * 8];
    private int sampleCount;
    private float sampleRate;

    private static float[] resample(float[] source, double sourceRate, double targetRate) {
        int outputLength = Math.max(1, (int) Math.round(source.length * targetRate / sourceRate));
        float[] output = new float[outputLength];
        double ratio = sourceRate / targetRate;
        double cutoff = Math.min(1, targetRate / sourceRate) * 0.94;
        double[][] kernels = resampleKernels(cutoff);
        int left = RESAMPLE_TAPS / 2 - 1;
        for (int outputIndex = 0; outputIndex < outputLength; outputIndex++) {
            double position = outputIndex * ratio;
            int center = (int) Math.floor(position);
            int phase = Math.min(RESAMPLE_PHASES - 1, (int) Math.round((position - center) * (RESAMPLE_PHASES - 1)));
            double sum = 0;
            double weight = 0;
            for (int tap = 0; tap < RESAMPLE_TAPS; tap++) {
                int sourceIndex = center + tap - left;
                if (sourceIndex >= 0 && sourceIndex < source.length) {
                    double coefficient = kernels[phase][tap];
                    sum += source[sourceIndex] * coefficient;
                    weight += coefficient;
                }
            }
            output[outputIndex] = weight == 0 ? 0 : (float) (sum / weight);
        }
        return output;
    }

    private static double[][] resampleKernels(double cutoff) {
        double[][] kernels = new double[RESAMPLE_PHASES][RESAMPLE_TAPS];
        int left = RESAMPLE_TAPS / 2 - 1;
        for (int phase = 0; phase < RESAMPLE_PHASES; phase++) {
            double fraction = phase / (double) (RESAMPLE_PHASES - 1);
            for (int tap = 0; tap < RESAMPLE_TAPS; tap++) {
                double x = tap - left - fraction;
                double sinc = Math.abs(x) < 1.0e-12 ? cutoff : Math.sin(Math.PI * cutoff * x) / (Math.PI * x);
                double normalized = x / (RESAMPLE_TAPS / 2.0);
                double window = Math.abs(normalized) >= 1 ? 0 : 0.42 + 0.5 * Math.cos(Math.PI * normalized) + 0.08 * Math.cos(2 * Math.PI * normalized);
                kernels[phase][tap] = sinc * window;
            }
        }
        return kernels;
    }

    private static double readSample(byte[] data, int offset, int bytes, boolean bigEndian) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytes; i++) {
                value = value << 8 | data[offset + i] & 0xff;
            }
        } else {
            for (int i = bytes - 1; i >= 0; i--) {
                value = value << 8 | data[offset + i] & 0xff;
            }
        }
        int shift = 32 - bytes * 8;
        return (value << shift >> shift) / (double) (1L << (bytes * 8 - 1));
    }

    void accept(byte[] data, int offset, int length, AudioFormat format) {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return;
        }
        int channels = Math.max(1, format.getChannels());
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        if (bytesPerSample < 1 || bytesPerSample > 4 || frameSize <= 0) {
            return;
        }
        if (sampleRate == 0) {
            sampleRate = format.getSampleRate();
        }
        if (Math.abs(sampleRate - format.getSampleRate()) > 0.5) {
            return;
        }
        int frames = length / frameSize;
        ensureCapacity(sampleCount + frames);
        int end = offset + length - frameSize + 1;
        for (int frameOffset = offset; frameOffset < end; frameOffset += frameSize) {
            double mono = 0;
            for (int channel = 0; channel < channels; channel++) {
                mono += readSample(data, frameOffset + channel * bytesPerSample, bytesPerSample, format.isBigEndian());
            }
            samples[sampleCount++] = (float) (mono / channels);
        }
    }

    BeatGrid analyze(long timelineOffsetMillis) throws OrtException, IOException {
        AudioAnalysis analysis = analyzeDetailed(timelineOffsetMillis);
        return analysis == null ? null : analysis.beatGrid();
    }

    AudioAnalysis analyzeDetailed(long timelineOffsetMillis) throws OrtException, IOException {
        if (sampleRate <= 0 || sampleCount < sampleRate * 4) {
            return null;
        }
        float[] mono = Arrays.copyOf(samples, sampleCount);
        float[] resampled = Math.abs(sampleRate - TARGET_RATE) < 0.5 ? mono : resample(mono, sampleRate, TARGET_RATE);
        BeatGrid beatGrid = Models.INSTANCE.analyze(resampled, timelineOffsetMillis);
        return beatGrid == null ? null : new AudioAnalysis(beatGrid, resampled);
    }

    private void ensureCapacity(int capacity) {
        if (capacity <= samples.length) {
            return;
        }
        int grown = Math.max(capacity, samples.length + samples.length / 2);
        samples = Arrays.copyOf(samples, grown);
    }

    record BeatGrid(double intervalMillis, double phaseMillis, double confidence, int beats, int downbeats,
                    double downbeatIntervalMillis, double downbeatPhaseMillis, List<Long> beatTimesMillis,
                    List<Long> downbeatTimesMillis) {

        BeatGrid {
            beatTimesMillis = List.copyOf(beatTimesMillis);
            downbeatTimesMillis = List.copyOf(downbeatTimesMillis);
        }
    }

    record AudioAnalysis(BeatGrid beatGrid, float[] audio) {
    }

    private record Models(OrtEnvironment environment, OrtSession melSession, OrtSession beatSession) {
        private static final Models INSTANCE = create();

        private static Models create() {
            try {
                OrtEnvironment environment = OrtEnvironment.getEnvironment("Tritium AutoMix");
                OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                options.setInterOpNumThreads(1);
                options.setIntraOpNumThreads(1);
                OrtSession mel = environment.createSession(readResource(MEL_MODEL_PATH), options);
                OrtSession beat = environment.createSession(readResource(MODEL_PATH), options);
                options.close();
                return new Models(environment, mel, beat);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to initialize Beat This!", e);
            }
        }

        private static float[] tensorValues(OrtSession.Result output, String name) {
            OnnxTensor tensor = (OnnxTensor) output.get(name).orElseThrow(() -> new IllegalStateException("Missing " + name + " output"));
            float[] values = new float[Math.toIntExact(tensor.getInfo().getNumElements())];
            tensor.getFloatBuffer().get(values);
            return values;
        }

        private static List<Integer> chunkStarts(int frames) {
            List<Integer> starts = new ArrayList<>();
            for (int start = -BORDER_SIZE; start < frames - BORDER_SIZE; start += STRIDE) {
                starts.add(start);
            }
            if (frames > STRIDE) {
                starts.set(starts.size() - 1, frames - (CHUNK_SIZE - BORDER_SIZE));
            }
            return starts;
        }

        private static Chunk extractChunk(float[] mel, int frames, int start) {
            int actualStart = Math.max(0, start);
            int actualEnd = Math.min(start + CHUNK_SIZE, frames);
            int leftPadding = Math.max(0, -start);
            int rightPadding = Math.max(0, Math.min(BORDER_SIZE, start + CHUNK_SIZE - frames));
            int chunkFrames = leftPadding + actualEnd - actualStart + rightPadding;
            float[] values = new float[chunkFrames * MEL_BANDS];
            for (int frame = actualStart; frame < actualEnd; frame++) {
                System.arraycopy(mel, frame * MEL_BANDS, values, (leftPadding + frame - actualStart) * MEL_BANDS, MEL_BANDS);
            }
            return new Chunk(values, chunkFrames);
        }

        private static List<Double> findPeaks(float[] logits) {
            List<Double> raw = new ArrayList<>();
            for (int frame = 0; frame < logits.length; frame++) {
                if (logits[frame] <= 0) {
                    continue;
                }
                boolean maximum = true;
                for (int nearby = Math.max(0, frame - 3); nearby < Math.min(logits.length, frame + 4); nearby++) {
                    if (logits[nearby] > logits[frame]) {
                        maximum = false;
                        break;
                    }
                }
                if (maximum) {
                    raw.add((double) frame);
                }
            }
            if (raw.isEmpty()) {
                return raw;
            }
            List<Double> merged = new ArrayList<>();
            double peak = raw.getFirst();
            int count = 1;
            for (int i = 1; i < raw.size(); i++) {
                double next = raw.get(i);
                if (next - peak <= 1) {
                    count++;
                    peak += (next - peak) / count;
                } else {
                    merged.add(peak / 50.0);
                    peak = next;
                    count = 1;
                }
            }
            merged.add(peak / 50.0);
            return merged;
        }

        private static BeatGrid beatGrid(List<Double> beats, List<Double> downbeats, long offsetMillis) {
            if (beats.size() < 4) {
                return null;
            }
            double[] intervals = new double[beats.size() - 1];
            int count = 0;
            for (int i = 1; i < beats.size(); i++) {
                double interval = beats.get(i) - beats.get(i - 1);
                if (interval > 0.1 && interval < 3) {
                    intervals[count++] = interval;
                }
            }
            if (count < 3) {
                return null;
            }
            intervals = Arrays.copyOf(intervals, count);
            double median = median(intervals);
            double[] deviations = new double[count];
            for (int i = 0; i < count; i++) {
                deviations[i] = Math.abs(intervals[i] - median);
            }
            double relativeDeviation = median(deviations) / Math.max(0.001, median);
            double consistency = Math.max(0, 1 - relativeDeviation * 5);
            double coverage = Math.min(1, count / 12.0);
            double confidence = Math.min(1, consistency * 0.78 + coverage * 0.22);
            double phaseSeconds = downbeats.isEmpty() ? beats.getFirst() : downbeats.getFirst();
            double downbeatIntervalMillis = 0;
            if (downbeats.size() >= 2) {
                double[] downbeatIntervals = new double[downbeats.size() - 1];
                for (int i = 1; i < downbeats.size(); i++) {
                    downbeatIntervals[i - 1] = downbeats.get(i) - downbeats.get(i - 1);
                }
                downbeatIntervalMillis = median(downbeatIntervals) * 1_000;
            }
            double downbeatPhaseMillis = downbeats.isEmpty() ? 0 : offsetMillis + downbeats.getFirst() * 1_000;
            List<Long> beatTimesMillis = beats.stream().map(time -> Math.round(offsetMillis + time * 1_000)).toList();
            List<Long> downbeatTimesMillis = downbeats.stream().map(time -> Math.round(offsetMillis + time * 1_000)).toList();
            return new BeatGrid(median * 1_000, offsetMillis + phaseSeconds * 1_000, confidence, beats.size(), downbeats.size(), downbeatIntervalMillis, downbeatPhaseMillis, beatTimesMillis, downbeatTimesMillis);
        }

        private static double median(double[] values) {
            double[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);
            int middle = sorted.length / 2;
            return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
        }

        private static byte[] readResource(String path) throws IOException {
            try (InputStream input = BeatThisTempoAnalyzer.class.getResourceAsStream(path)) {
                if (input == null) {
                    throw new IOException("Missing resource " + path);
                }
                return input.readAllBytes();
            }
        }

        private synchronized BeatGrid analyze(float[] audio, long timelineOffsetMillis) throws OrtException {
            MelData mel = extractMel(audio);
            float[] beatLogits = new float[mel.frames];
            float[] downbeatLogits = new float[mel.frames];
            Arrays.fill(beatLogits, -1_000);
            Arrays.fill(downbeatLogits, -1_000);
            List<Integer> starts = chunkStarts(mel.frames);
            for (int startIndex = starts.size() - 1; startIndex >= 0; startIndex--) {
                int start = starts.get(startIndex);
                Chunk chunk = extractChunk(mel.values, mel.frames, start);
                try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(chunk.values), new long[]{1, chunk.frames, MEL_BANDS}); OrtSession.Result output = beatSession.run(Map.of("input_spectrogram", input))) {
                    float[] beats = tensorValues(output, "beat");
                    float[] downbeats = tensorValues(output, "downbeat");
                    int writeStart = start + BORDER_SIZE;
                    for (int frame = BORDER_SIZE; frame < chunk.frames - BORDER_SIZE; frame++) {
                        int target = writeStart + frame - BORDER_SIZE;
                        if (target >= 0 && target < mel.frames) {
                            beatLogits[target] = beats[frame];
                            downbeatLogits[target] = downbeats[frame];
                        }
                    }
                }
            }
            List<Double> beats = findPeaks(beatLogits);
            List<Double> downbeats = findPeaks(downbeatLogits);
            return beatGrid(beats, downbeats, timelineOffsetMillis);
        }

        private MelData extractMel(float[] audio) throws OrtException {
            try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(audio), new long[]{1, audio.length}); OrtSession.Result output = melSession.run(Map.of("audio_pcm", input))) {
                OnnxTensor tensor = (OnnxTensor) output.get("mel_spectrogram").orElseThrow(() -> new IllegalStateException("Missing mel_spectrogram output"));
                long[] shape = tensor.getInfo().getShape();
                float[] values = new float[Math.toIntExact(tensor.getInfo().getNumElements())];
                tensor.getFloatBuffer().get(values);
                return new MelData(values, Math.toIntExact(shape[1]));
            }
        }

        private record MelData(float[] values, int frames) {
        }

        private record Chunk(float[] values, int frames) {
        }
    }
}
