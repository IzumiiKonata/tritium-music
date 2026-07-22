package tritium.music.core.audio;

import lombok.Getter;
import tritium.music.core.MusicState;
import tritium.music.repackage.processing.sound.Engine;
import tritium.music.repackage.processing.sound.FFT;
import tritium.music.repackage.processing.sound.JSynFFT;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class AudioPlayer {

    private StreamingSoundPlayer player;
    public Runnable afterPlayed;

    /**
     * Spectrum band magnitudes, updated by the FFT analysis. Empty until the first FFT frame.
     */
    public static float[] bandValues = new float[0];

    private static final int BAR_COUNT = 128;

    /**
     * Gate for the FFT callback so analysis only runs when something consumes the bands
     * (spectrum widget visible or a lyrics/now-playing surface open). Set by the client.
     */
    public static volatile boolean spectrumEnabled = false;

    /** Spectrum visualizer tuning, set by the client from config. */
    public static volatile float spectrumTilt = 3.0f;
    public static volatile boolean absoluteVolume = true;

    public enum WaveMode {
        None,
        Waveform,
        Oscilloscope
    }

    /** Active waveform/oscilloscope mode, set by the client from config. */
    public static volatile WaveMode waveMode = WaveMode.None;

    /** Waveform/oscilloscope capture window in milliseconds, set by the client from config. */
    public static volatile float windowTime = 16.0f;

    /** Split waveform/oscilloscope into left/right halves, set by the client from config. */
    public static volatile boolean stereo = false;

    /** Pixel region the client draws the waveform into, pushed each frame before doDetections(). */
    public static volatile double waveRegionWidth = 200;
    public static volatile double waveRegionHeight = 80;

    private static int skipCount = 0;

    private final SpectrumVisualizer visualizer = new SpectrumVisualizer(JSynFFT.FFT_SIZE, BAR_COUNT);
    private final float[] fftWindow = new float[JSynFFT.FFT_SIZE];
    private int fftWindowOffset;
    private int fftSamplesSinceAnalysis;
    private int waveWriteIndex;

    public float[] wave, waveRight;
    public float[] waveVertexes, waveRightVertexes;
    public float[] osc, oscRight;

    public final ReentrantLock lockL = new ReentrantLock(), lockR = new ReentrantLock();
    public volatile boolean spectrumDataLFilled = false, spectrumDataRFilled = false;

    private OscilloscopeState oscStateL;
    private OscilloscopeState oscStateR;

    private static final int OSC_TARGET_TAPS = 384;
    private static final float OSC_EDGE_STRENGTH = 0.8f;
    private static final float OSC_BUFFER_STRENGTH = 1.0f;
    private static final float OSC_RESPONSIVENESS = 0.4f;

    private float configuredWindowTime = windowTime;

    public static class OscilloscopeState {
        final int stride;
        final int wd;
        final int nd;
        final float[] corrected;
        final float[] ds;
        final float[] corrBuffer;
        final float[] kernel;
        final float[] slopeFinder;
        final float[] bufferWindow;

        OscilloscopeState(int captureSamples, int displaySamples) {
            int s = Math.max(1, Math.round(displaySamples / (float) OSC_TARGET_TAPS));
            this.stride = s;
            this.wd = Math.max(8, displaySamples / s);
            this.nd = captureSamples / s;

            this.corrected = new float[captureSamples];
            this.ds = new float[nd];
            this.corrBuffer = new float[wd];
            this.kernel = new float[wd];
            this.slopeFinder = new float[wd];
            this.bufferWindow = new float[wd];

            float center = (wd - 1) * 0.5f;
            float slopeStd = Math.max(1f, wd * 0.10f);
            float winStd = Math.max(1f, wd * 0.35f);
            int half = wd / 2;

            for (int k = 0; k < wd; k++) {
                float ds = (k - center) / slopeStd;
                float g = (float) Math.exp(-0.5f * ds * ds);
                slopeFinder[k] = (k < half ? -OSC_EDGE_STRENGTH : OSC_EDGE_STRENGTH) * g;

                float dw = (k - center) / winStd;
                bufferWindow[k] = (float) Math.exp(-0.5f * dw * dw);
            }
        }
    }

    @Getter
    public float volume = 0.25f;

    public AudioPlayer(File file, long durationMillis) {
        finished = false;
        this.player = new StreamingSoundPlayer(file, durationMillis, this::onPcm);
        this.setListeners();
    }

    public AudioPlayer(String url, String type, long durationMillis) {
        finished = false;
        this.player = new StreamingSoundPlayer(url, type, durationMillis, this::onPcm);
        this.setListeners();
    }

    public void setAudio(File file, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(file, durationMillis, this::onPcm);
        this.setListeners();
        finished = false;
    }

    public void setAudio(String url, String type, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(url, type, durationMillis, this::onPcm);
        this.setListeners();
        finished = false;
    }

    public void setListeners() {
        int sampleRate = Engine.getEngine().getSampleRate();
        float windowSeconds = windowTime * 0.001f;
        int displaySamples = Math.max(2, (int) (sampleRate * windowSeconds));
        int triggerSearch = displaySamples;
        int captureSamples = displaySamples + triggerSearch;

        oscStateL = new OscilloscopeState(captureSamples, displaySamples);
        oscStateR = new OscilloscopeState(captureSamples, displaySamples);

        lockL.lock();
        wave = new float[captureSamples];
        waveVertexes = new float[displaySamples * 2];
        osc = new float[displaySamples];
        lockL.unlock();

        lockR.lock();
        waveRight = new float[captureSamples];
        waveRightVertexes = new float[displaySamples * 2];
        oscRight = new float[displaySamples];
        lockR.unlock();

        configuredWindowTime = windowTime;
        fftWindowOffset = 0;
        fftSamplesSinceAnalysis = 0;
        waveWriteIndex = 0;
        Arrays.fill(fftWindow, 0);
        player.setOnFinished(() -> finished = true);
        player.setOnFailed(() -> {
            failed = true;
            finished = true;
        });
    }

    public void doDetections() {
        if (this.isPausing()) {
            return;
        }

        WaveMode mode = waveMode;
        if (mode == WaveMode.None) {
            return;
        }

        if (mode != WaveMode.Oscilloscope && !spectrumEnabled) {
            return;
        }

        if (configuredWindowTime != windowTime) {
            setListeners();
            spectrumDataLFilled = spectrumDataRFilled = false;
        }

        float[] left;
        float[] right;
        lockL.lock();
        lockR.lock();
        try {
            left = orderedWave(wave, waveWriteIndex);
            right = orderedWave(waveRight, waveWriteIndex);
        } finally {
            lockR.unlock();
            lockL.unlock();
        }

        if (mode == WaveMode.Waveform) {
            computeVertexes(left, waveVertexes);
            computeVertexes(right, waveRightVertexes);
        } else {
            computeOscilloscopeVertexes(left, osc, waveVertexes, oscStateL);
            computeOscilloscopeVertexes(right, oscRight, waveRightVertexes, oscStateR);
        }

        spectrumDataLFilled = true;
        spectrumDataRFilled = true;
    }

    public void computeVertexes(float[] input, float[] output) {
        int display = output.length / 2;
        int offset = Math.max(0, input.length - display);

        double spacing = (waveRegionWidth - 8) / (double) display;
        double oscHeight = (stereo ? (waveRegionHeight - 17) * 0.5 : (waveRegionHeight - 17)) - 4;
        float volumeScale = absoluteVolume ? volume : (.5f + volume * 1.75f);

        for (int i = 0; i < display; i++) {
            float v = input[offset + i];
            int outputIdx = i * 2;
            output[outputIdx] = (float) (spacing * i);
            output[outputIdx + 1] = (float) (oscHeight * v / volumeScale);
        }
    }

    public void computeOscilloscopeVertexes(float[] input, float[] output, float[] vertexes, OscilloscopeState state) {
        int n = input.length;
        int display = output.length;

        int stride = state.stride;
        int wd = state.wd;
        int nd = Math.min(state.nd, n / stride);
        int searchD = Math.max(1, nd - wd);

        float[] corrected = state.corrected;
        float[] ds = state.ds;
        float[] buf = state.corrBuffer;
        float[] kernel = state.kernel;
        float[] slope = state.slopeFinder;
        float[] window = state.bufferWindow;

        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += input[i];
        }
        float mean = (float) (sum / n);
        for (int i = 0; i < n; i++) {
            corrected[i] = input[i] - mean;
        }

        double energy = 0;
        for (int j = 0; j < nd; j++) {
            int base = j * stride;
            float s = 0;
            int cnt = 0;
            for (int k = 0; k < stride; k++) {
                int idx = base + k;
                if (idx < n) {
                    s += corrected[idx];
                    cnt++;
                }
            }
            float v = cnt > 0 ? s / cnt : 0f;
            ds[j] = v;
            energy += (double) v * v;
        }
        float dsRms = (float) Math.sqrt(energy / nd);

        int triggerFull;

        if (dsRms < 1.0e-6f) {
            triggerFull = 0;
        } else {
            for (int k = 0; k < wd; k++) {
                kernel[k] = slope[k] + OSC_BUFFER_STRENGTH * buf[k];
            }

            int bestOff = 0;
            double bestScore = -Double.MAX_VALUE;
            for (int off = 0; off <= searchD; off++) {
                double score = 0;
                for (int k = 0; k < wd; k++) {
                    score += ds[off + k] * kernel[k];
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestOff = off;
                }
            }

            triggerFull = bestOff * stride;

            double a2 = 0;
            for (int k = 0; k < wd; k++) {
                float v = ds[bestOff + k];
                a2 += (double) v * v;
            }
            float aRms = (float) Math.sqrt(a2 / wd);
            if (aRms > 1.0e-6f) {
                float ainv = 1f / aRms;
                for (int k = 0; k < wd; k++) {
                    float val = ds[bestOff + k] * ainv * window[k];
                    buf[k] += OSC_RESPONSIVENESS * (val - buf[k]);
                }

                double b2 = 0;
                for (int k = 0; k < wd; k++) {
                    b2 += (double) buf[k] * buf[k];
                }
                if (b2 > 1.0e-9) {
                    float bn = (float) (1.0 / Math.sqrt(b2 / wd));
                    for (int k = 0; k < wd; k++) {
                        buf[k] *= bn;
                    }
                }
            }
        }

        if (triggerFull > n - display) {
            triggerFull = n - display;
        }
        if (triggerFull < 0) {
            triggerFull = 0;
        }

        for (int j = 0; j < display; j++) {
            output[j] = corrected[triggerFull + j];
        }

        double spacing = (waveRegionWidth - 8) / (double) display;
        double oscHeight = (stereo ? (waveRegionHeight - 17) * 0.5 : (waveRegionHeight - 17)) - 4;
        float volumeScale = absoluteVolume ? volume : (.5f + volume * 1.75f);

        for (int j = 0; j < display; j++) {
            int vi = j * 2;
            vertexes[vi] = (float) (spacing * j);
            vertexes[vi + 1] = (float) (oscHeight * output[j] / volumeScale);
        }
    }

    private void onFFT(float[] magnitudes) {
        if (!spectrumEnabled) {
            return;
        }

        int skipAmount = 4;
        if (skipCount < skipAmount) {
            skipCount++;
            return;
        }
        skipCount = 0;

        visualizer.setVolume(this.volume);
        visualizer.setSpectrumTilt(spectrumTilt);
        visualizer.setAbsoluteVolume(absoluteVolume);
        bandValues = visualizer.processFFT(magnitudes);
    }

    private void onPcm(byte[] data, int offset, int length, AudioFormat format) {
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        int frameCount = length / frameSize;
        lockL.lock();
        lockR.lock();
        try {
            for (int frame = 0; frame < frameCount; frame++) {
                int base = offset + frame * frameSize;
                float left = readSample(data, base, bytesPerSample, format.isBigEndian());
                float right = channels > 1 ? readSample(data, base + bytesPerSample, bytesPerSample, format.isBigEndian()) : left;
                wave[waveWriteIndex] = left;
                waveRight[waveWriteIndex] = right;
                waveWriteIndex = (waveWriteIndex + 1) % wave.length;
                fftWindow[fftWindowOffset++] = (left + right) * 0.5f;
                if (fftWindowOffset == fftWindow.length) {
                    fftWindowOffset = 0;
                }
                fftSamplesSinceAnalysis++;
            }
            if (spectrumEnabled && fftSamplesSinceAnalysis >= BAR_COUNT) {
                float[] ordered = new float[fftWindow.length];
                int tail = fftWindow.length - fftWindowOffset;
                System.arraycopy(fftWindow, fftWindowOffset, ordered, 0, tail);
                System.arraycopy(fftWindow, 0, ordered, tail, fftWindowOffset);
                onFFT(FFT.analyzeSample(ordered, fftWindow.length));
                fftSamplesSinceAnalysis = 0;
            }
        } finally {
            lockR.unlock();
            lockL.unlock();
        }
    }

    private static float readSample(byte[] data, int offset, int bytes, boolean bigEndian) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytes; i++) {
                value = (value << 8) | (data[offset + i] & 0xff);
            }
        } else {
            for (int i = bytes - 1; i >= 0; i--) {
                value = (value << 8) | (data[offset + i] & 0xff);
            }
        }
        int shift = 32 - bytes * 8;
        return (value << shift >> shift) / (float) (1L << (bytes * 8 - 1));
    }

    private static float[] orderedWave(float[] source, int offset) {
        float[] ordered = new float[source.length];
        int tail = source.length - offset;
        System.arraycopy(source, offset, ordered, 0, tail);
        System.arraycopy(source, 0, ordered, tail, offset);
        return ordered;
    }

    public void play() {
        finished = false;
        failed = false;
        this.player.play();
        this.player.setVolume(volume);
    }

    public void setPlaybackTime(float millis) {
        this.player.seek((long) millis);
        this.player.setVolume(volume);
    }

    public void close() {
        this.player.close();
    }

    @Getter
    private boolean finished;

    @Getter
    private boolean failed;

    public void setAfterPlayed(Runnable runnable) {
        this.afterPlayed = runnable;
        this.player.setOnFinished(() -> {
            finished = true;
            runnable.run();
        });
        this.player.setOnFailed(() -> {
            failed = true;
            finished = true;
            runnable.run();
        });
    }

    public float getTotalTimeSeconds() {
        return this.player.durationMillis() / 1000f;
    }

    public float getCurrentTimeSeconds() {
        return (int) (getCurrentTimeMillis() / 1000);
    }

    public float getTotalTimeMillis() {
        return getTotalTimeSeconds() * 1000;
    }

    public float getCurrentTimeMillis() {
        return this.player.positionMillis();
    }

    public boolean isPausing() {
        return !this.player.isPlaying();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        MusicState.get().setVolume(volume);
        this.player.setVolume(this.getVolume());
    }

    public void pause() {
        this.player.pause();
    }

    public void unpause() {
        this.play();
    }

    public boolean isPlaying() {
        return this.player.isPlaying();
    }
}
