package tritium.music.core.audio;

import lombok.Getter;
import lombok.Setter;
import tritium.music.repackage.processing.sound.Engine;

public class SpectrumVisualizer {

    private static final float MIN_FREQ = 20.0f;
    private static final float MAX_FREQ = 20000.0f;

    private static final float FLOOR_DB = -72.0f;
    private static final float CEIL_DB = -6.0f;
    private static final float PIVOT_FREQ = 1000.0f;

    private static final double LOG2 = Math.log(2.0);

    @Getter
    private final int fftSize;
    @Getter
    private final int numBars;
    private final int usableBins;

    private int sampleRate = -1;

    private float[] edgeBinLow;
    private float[] edgeBinHigh;
    private float[] centerFreq;

    private final float[] output;
    private final float[] smoothed;

    @Setter
    private float spectrumTilt = 3.0f;
    @Setter
    private boolean absoluteVolume = true;
    @Setter
    private double volume = 0.1;

    public SpectrumVisualizer(int fftSize, int numBars) {
        this.fftSize = fftSize;
        this.numBars = numBars;
        this.usableBins = fftSize / 2;
        this.output = new float[numBars];
        this.smoothed = new float[numBars];
    }

    private void rebuild(int sampleRate) {
        this.sampleRate = sampleRate;

        if (edgeBinLow == null) {
            edgeBinLow = new float[numBars];
            edgeBinHigh = new float[numBars];
            centerFreq = new float[numBars];
        }

        float nyquist = sampleRate * 0.5f;
        float maxFreq = Math.min(MAX_FREQ, nyquist * 0.98f);
        float minFreq = Math.min(MIN_FREQ, maxFreq * 0.5f);

        double logRatio = Math.log(maxFreq / minFreq);
        float binsPerHz = (float) fftSize / sampleRate;

        for (int b = 0; b < numBars; b++) {
            float lowFreq = (float) (minFreq * Math.exp(logRatio * b / numBars));
            float highFreq = (float) (minFreq * Math.exp(logRatio * (b + 1) / numBars));

            edgeBinLow[b] = lowFreq * binsPerHz;
            edgeBinHigh[b] = highFreq * binsPerHz;
            centerFreq[b] = (float) Math.sqrt(lowFreq * highFreq);
        }
    }

    public float[] processFFT(float[] magnitudes) {
        int sr = Engine.getEngine().getSampleRate();
        if (sr <= 0) {
            sr = 44100;
        }

        if (sr != sampleRate || edgeBinLow == null) {
            rebuild(sr);
        }

        float tilt = spectrumTilt;
        float volumeComp = absoluteVolume ? (float) (-20.0 * Math.log10(Math.max(volume, 1.0e-3))) : 0.0f;

        int maxBin = Math.min(usableBins - 1, magnitudes.length - 1);

        for (int b = 0; b < numBars; b++) {
            float low = edgeBinLow[b];
            float high = edgeBinHigh[b];

            double power;

            if (high - low < 1.0f) {
                float center = (low + high) * 0.5f;
                int i0 = (int) Math.floor(center);
                if (i0 < 0) i0 = 0;
                if (i0 > maxBin) i0 = maxBin;
                int i1 = Math.min(i0 + 1, maxBin);

                float frac = center - i0;
                float m = magnitudes[i0] + (magnitudes[i1] - magnitudes[i0]) * frac;
                power = (double) m * m;
            } else {
                int binLo = Math.max(0, Math.round(low));
                int binHi = Math.min(maxBin, Math.round(high));
                if (binHi < binLo) binHi = binLo;

                double sum = 0.0;
                for (int bin = binLo; bin <= binHi; bin++) {
                    float m = magnitudes[bin];
                    sum += (double) m * m;
                }
                power = sum / (binHi - binLo + 1);
            }

            double amplitude = Math.sqrt(power) * 2.0;
            float db = (float) (20.0 * Math.log10(amplitude + 1.0e-9));

            db += volumeComp;
            db += tilt * (float) (Math.log(centerFreq[b] / PIVOT_FREQ) / LOG2);

            float normalized = (db - FLOOR_DB) / (CEIL_DB - FLOOR_DB);
            if (normalized < 0.0f) normalized = 0.0f;
            if (normalized > 1.0f) normalized = 1.0f;

            output[b] = normalized;
        }

        for (int b = 0; b < numBars; b++) {
            float l = output[Math.max(0, b - 1)];
            float c = output[b];
            float r = output[Math.min(numBars - 1, b + 1)];
            smoothed[b] = (l + 2.0f * c + r) * 0.25f;
        }
        System.arraycopy(smoothed, 0, output, 0, numBars);

        return output;
    }
}
