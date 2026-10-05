package tritium.music.core.audio;

import java.util.Arrays;
import java.util.List;

final class BeatAccentAnalyzer {

    static final int SAMPLE_RATE = 22_050;

    private static final double REFERENCE_PERCENTILE = 0.72;
    private static final double ONSET_WEIGHT = 2.4;
    private static final int LEAD_IN_SAMPLES = SAMPLE_RATE / 100;
    private static final int TAIL_SAMPLES = SAMPLE_RATE * 9 / 100;
    private static final double LOW_BAND_HZ = 190;
    private static final int FILTER_WARMUP_SAMPLES = SAMPLE_RATE / 20;

    record Accents(double[] wideband, double[] lowBand) {

        double blended(int index, double lowBandWeight) {
            return wideband[index] * (1 - lowBandWeight) + lowBand[index] * lowBandWeight;
        }
    }

    private BeatAccentAnalyzer() {
    }

    static double[] accents(float[] audio, long timelineOffsetMillis, List<Long> beats) {
        return measure(audio, timelineOffsetMillis, beats).wideband();
    }

    static Accents measure(float[] audio, long timelineOffsetMillis, List<Long> beats) {
        double[] wideband = new double[beats.size()];
        double[] lowBand = new double[beats.size()];
        double lowAlpha = 1 - Math.exp(-Math.PI * 2 * LOW_BAND_HZ / SAMPLE_RATE);
        for (int beat = 0; beat < beats.size(); beat++) {
            int center = (int) Math.round((beats.get(beat) - timelineOffsetMillis) * SAMPLE_RATE / 1_000.0);
            int start = Math.max(1, center - LEAD_IN_SAMPLES);
            int end = Math.min(audio.length, center + TAIL_SAMPLES);
            double energy = 0;
            double transientEnergy = 0;
            double lowEnergy = 0;
            double lowTransientEnergy = 0;
            double lowState = 0;
            double previousLow = 0;
            for (int sample = Math.max(1, start - FILTER_WARMUP_SAMPLES); sample < end; sample++) {
                double value = audio[sample];
                lowState += lowAlpha * (value - lowState);
                if (sample < start) {
                    previousLow = lowState;
                    continue;
                }
                double difference = value - audio[sample - 1];
                energy += value * value;
                transientEnergy += difference * difference;
                double lowDifference = lowState - previousLow;
                lowEnergy += lowState * lowState;
                lowTransientEnergy += lowDifference * lowDifference;
                previousLow = lowState;
            }
            int count = Math.max(1, end - start);
            wideband[beat] = Math.sqrt(energy / count) + Math.sqrt(transientEnergy / count) * ONSET_WEIGHT;
            lowBand[beat] = Math.sqrt(lowEnergy / count) + Math.sqrt(lowTransientEnergy / count) * ONSET_WEIGHT;
        }
        return new Accents(normalize(wideband), normalize(lowBand));
    }

    private static double[] normalize(double[] raw) {
        if (raw.length == 0) {
            return raw;
        }
        double[] sorted = Arrays.copyOf(raw, raw.length);
        Arrays.sort(sorted);
        double reference = Math.max(1.0e-6, sorted[(int) Math.floor((sorted.length - 1) * REFERENCE_PERCENTILE)]);
        double[] result = new double[raw.length];
        for (int index = 0; index < raw.length; index++) {
            result[index] = Math.max(0, Math.min(1, raw[index] / reference));
        }
        return result;
    }
}
