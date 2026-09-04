package tritium.music.core.audio;

import lombok.Getter;
import tritium.music.core.MusicState;
import tritium.music.repackage.processing.sound.FFT;
import tritium.music.repackage.processing.sound.JSynFFT;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioPlayer {

    private static final int BAR_COUNT = 128;
    private static final int FFT_HOP_SAMPLES = BAR_COUNT * 5;
    private static final float[] FFT_WINDOW = createFftWindow();
    private static final ExecutorService FFT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Music Spectrum Analyzer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static volatile AudioPlayer spectrumSource;
    /**
     * Spectrum band magnitudes, updated by the FFT analysis. Empty until the first FFT frame.
     */
    public static volatile float[] bandValues = new float[0];
    /**
     * Gate for the FFT callback so analysis only runs when something consumes the bands
     * (spectrum widget visible or a lyrics/now-playing surface open). Set by the client.
     */
    public static volatile boolean spectrumEnabled = false;
    /**
     * Spectrum visualizer tuning, set by the client from config.
     */
    public static volatile float spectrumTilt = 3.0f;
    public static volatile boolean absoluteVolume = true;
    private final SpectrumVisualizer visualizer = new SpectrumVisualizer(JSynFFT.FFT_SIZE, BAR_COUNT);
    private final float[] fftWindow = new float[JSynFFT.FFT_SIZE];
    private final AtomicBoolean spectrumTaskQueued = new AtomicBoolean();
    private volatile float[] pendingSpectrumWindow;
    public Runnable afterPlayed;
    @Getter
    public float volume = 0.25f;
    private StreamingSoundPlayer player;
    private volatile AutoMixAnalyzer autoMixAnalyzer = new AutoMixAnalyzer();
    private int fftWindowOffset;
    private int fftSamplesSinceAnalysis;
    private volatile float mixGain = 1;
    private volatile float normalizationGain = 1;
    private volatile float transitionLowGain = 1;
    private volatile float transitionMidGain = 1;
    private volatile float transitionHighGain = 1;
    private volatile float transitionLowPassHz;
    private double[] transitionLowState = new double[0];
    private double[] transitionUpperState = new double[0];
    private double[] transitionOutputState = new double[0];
    @Getter
    private boolean finished;
    @Getter
    private boolean failed;

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

    private static float[] createFftWindow() {
        float[] window = new float[JSynFFT.FFT_SIZE];
        for (int i = 0; i < window.length; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (window.length - 1)));
        }
        return window;
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

    private static void writeSample(byte[] data, int offset, int bytes, boolean bigEndian, double sample) {
        long limit = (1L << (bytes * 8 - 1)) - 1;
        long value = Math.round(Math.max(-1, Math.min(1, sample)) * limit);
        if (bigEndian) {
            for (int i = bytes - 1; i >= 0; i--) {
                data[offset + i] = (byte) value;
                value >>= 8;
            }
        } else {
            for (int i = 0; i < bytes; i++) {
                data[offset + i] = (byte) value;
                value >>= 8;
            }
        }
    }

    public void setAudio(File file, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(file, durationMillis, this::onPcm);
        resetAutoMixState();
        this.setListeners();
        finished = false;
    }

    public void setAudio(String url, String type, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(url, type, durationMillis, this::onPcm);
        resetAutoMixState();
        this.setListeners();
        finished = false;
    }

    public void setListeners() {
        fftWindowOffset = 0;
        fftSamplesSinceAnalysis = 0;
        Arrays.fill(fftWindow, 0);
        player.setOnFinished(() -> finished = true);
        player.setOnFailed(() -> {
            failed = true;
            finished = true;
        });
    }

    private void onFFT(float[] magnitudes) {
        if (!spectrumEnabled) {
            return;
        }

        visualizer.setVolume(this.volume);
        visualizer.setSpectrumTilt(spectrumTilt);
        visualizer.setAbsoluteVolume(absoluteVolume);
        bandValues = visualizer.processFFT(magnitudes);
    }

    private void onPcm(byte[] data, int offset, int length, AudioFormat format) {
        autoMixAnalyzer.accept(data, offset, length, format);
        applyTransitionFilter(data, offset, length, format);
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        int frameCount = length / frameSize;
        float playbackVolume = effectiveVolume();
        for (int frame = 0; frame < frameCount; frame++) {
            int base = offset + frame * frameSize;
            float left = readSample(data, base, bytesPerSample, format.isBigEndian()) * playbackVolume;
            float right = channels > 1
                    ? readSample(data, base + bytesPerSample, bytesPerSample, format.isBigEndian()) * playbackVolume
                    : left;
            fftWindow[fftWindowOffset++] = (left + right) * 0.5f;
            if (fftWindowOffset == fftWindow.length) {
                fftWindowOffset = 0;
            }
            if (spectrumEnabled && spectrumSource == this) {
                fftSamplesSinceAnalysis++;
                if (fftSamplesSinceAnalysis >= FFT_HOP_SAMPLES) {
                    publishSpectrum();
                    fftSamplesSinceAnalysis -= FFT_HOP_SAMPLES;
                }
            } else {
                fftSamplesSinceAnalysis = 0;
            }
        }
    }

    private void publishSpectrum() {
        float[] ordered = new float[fftWindow.length];
        int tail = fftWindow.length - fftWindowOffset;
        System.arraycopy(fftWindow, fftWindowOffset, ordered, 0, tail);
        System.arraycopy(fftWindow, 0, ordered, tail, fftWindowOffset);
        for (int i = 0; i < ordered.length; i++) {
            ordered[i] *= FFT_WINDOW[i];
        }
        pendingSpectrumWindow = ordered;
        queueSpectrumTask();
    }

    private void queueSpectrumTask() {
        if (!spectrumTaskQueued.compareAndSet(false, true)) {
            return;
        }
        FFT_EXECUTOR.execute(() -> {
            try {
                float[] samples;
                while ((samples = pendingSpectrumWindow) != null) {
                    pendingSpectrumWindow = null;
                    float[] magnitudes = FFT.analyzeSample(samples, samples.length);
                    for (int i = 0; i < magnitudes.length; i++) {
                        magnitudes[i] *= 2.0f;
                    }
                    if (spectrumSource == this) {
                        onFFT(magnitudes);
                    }
                }
            } finally {
                spectrumTaskQueued.set(false);
                if (pendingSpectrumWindow != null) {
                    queueSpectrumTask();
                }
            }
        });
    }

    public void activateSpectrum() {
        spectrumSource = this;
        fftSamplesSinceAnalysis = 0;
    }

    public void play() {
        finished = false;
        failed = false;
        this.player.play();
        refreshOutputVolume();
    }

    public void prepare() {
        finished = false;
        failed = false;
        this.player.prepare();
        refreshOutputVolume();
    }

    public boolean awaitPrepared(long timeoutMillis) throws InterruptedException {
        return this.player.awaitPrepared(timeoutMillis);
    }

    public AutoMixTrackAnalysis analyzeForAutoMix(long startMillis, long maxMillis) throws java.io.IOException {
        return this.player.analyzeForAutoMix(startMillis, maxMillis);
    }

    public AutoMixProfile getAutoMixProfile() {
        return autoMixAnalyzer.snapshot();
    }

    public void setPlaybackTime(float millis) {
        autoMixAnalyzer = new AutoMixAnalyzer((long) millis);
        this.player.seek((long) millis);
        refreshOutputVolume();
    }

    public void close() {
        this.player.close();
    }

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
        refreshOutputVolume();
    }

    public void setMixGain(float mixGain) {
        this.mixGain = Math.max(0, Math.min(mixGain, 1));
        refreshOutputVolume();
    }

    public void setNormalizationGain(float normalizationGain) {
        this.normalizationGain = Math.max(0.63f, Math.min(normalizationGain, 1.58f));
        refreshOutputVolume();
    }

    public void setTransitionEq(float lowGain, float midGain, float highGain, float lowPassHz) {
        transitionLowGain = Math.max(0, Math.min(lowGain, 2));
        transitionMidGain = Math.max(0, Math.min(midGain, 2));
        transitionHighGain = Math.max(0, Math.min(highGain, 2));
        transitionLowPassHz = lowPassHz <= 0 ? 0 : Math.max(400, Math.min(lowPassHz, 20_000));
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

    public void setPlaybackRate(double playbackRate) {
        this.player.setPlaybackRate(playbackRate);
    }

    public void setPitchShiftSemitones(double pitchShiftSemitones) {
        this.player.setPitchShiftSemitones(pitchShiftSemitones);
    }

    private void refreshOutputVolume() {
        this.player.setVolume(effectiveVolume());
    }

    private float effectiveVolume() {
        return Math.max(0, Math.min(1, volume * mixGain * normalizationGain));
    }

    private void resetAutoMixState() {
        autoMixAnalyzer = new AutoMixAnalyzer();
        mixGain = 1;
        normalizationGain = 1;
        transitionLowGain = 1;
        transitionMidGain = 1;
        transitionHighGain = 1;
        transitionLowPassHz = 0;
        transitionLowState = new double[0];
        transitionUpperState = new double[0];
        transitionOutputState = new double[0];
    }

    void applyTransitionFilter(byte[] data, int offset, int length, AudioFormat format) {
        float lowGain = transitionLowGain;
        float midGain = transitionMidGain;
        float highGain = transitionHighGain;
        float lowPassHz = transitionLowPassHz;
        if ((Math.abs(lowGain - 1) < 0.0001f && Math.abs(midGain - 1) < 0.0001f
                && Math.abs(highGain - 1) < 0.0001f && lowPassHz <= 0)
                || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return;
        }
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        if (bytesPerSample < 1 || bytesPerSample > 4) {
            return;
        }
        boolean initializeState = transitionLowState.length != channels;
        if (initializeState) {
            transitionLowState = new double[channels];
            transitionUpperState = new double[channels];
            transitionOutputState = new double[channels];
        }
        double lowAlpha = 1 - Math.exp(-Math.PI * 2 * 220 / format.getSampleRate());
        double upperAlpha = 1 - Math.exp(-Math.PI * 2 * 4_200 / format.getSampleRate());
        double outputAlpha = lowPassHz <= 0
                ? 1
                : 1 - Math.exp(-Math.PI * 2 * lowPassHz / format.getSampleRate());
        int end = offset + length - frameSize + 1;
        for (int frameOffset = offset; frameOffset < end; frameOffset += frameSize) {
            for (int channel = 0; channel < channels; channel++) {
                int sampleOffset = frameOffset + channel * bytesPerSample;
                double input = readSample(data, sampleOffset, bytesPerSample, format.isBigEndian());
                if (initializeState && frameOffset == offset) {
                    transitionLowState[channel] = input;
                    transitionUpperState[channel] = input;
                    transitionOutputState[channel] = input;
                }
                transitionLowState[channel] += lowAlpha * (input - transitionLowState[channel]);
                transitionUpperState[channel] += upperAlpha * (input - transitionUpperState[channel]);
                double low = transitionLowState[channel];
                double mid = transitionUpperState[channel] - low;
                double high = input - transitionUpperState[channel];
                double mixed = low * lowGain + mid * midGain + high * highGain;
                transitionOutputState[channel] += outputAlpha * (mixed - transitionOutputState[channel]);
                double output = transitionOutputState[channel];
                writeSample(data, sampleOffset, bytesPerSample, format.isBigEndian(), output);
            }
        }
    }
}
