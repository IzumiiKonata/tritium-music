package tritium.music.core.audio;

import lombok.Getter;
import tritium.music.core.MusicState;
import tritium.music.repackage.processing.sound.FFT;
import tritium.music.repackage.processing.sound.JSynFFT;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Arrays;

public class AudioPlayer {

    private StreamingSoundPlayer player;
    public Runnable afterPlayed;

    /**
     * Spectrum band magnitudes, updated by the FFT analysis. Empty until the first FFT frame.
     */
    public static volatile float[] bandValues = new float[0];

    private static final int BAR_COUNT = 128;
    private static final int FFT_HOP_SAMPLES = BAR_COUNT * 5;
    private static final float[] FFT_WINDOW = createFftWindow();

    /**
     * Gate for the FFT callback so analysis only runs when something consumes the bands
     * (spectrum widget visible or a lyrics/now-playing surface open). Set by the client.
     */
    public static volatile boolean spectrumEnabled = false;

    /** Spectrum visualizer tuning, set by the client from config. */
    public static volatile float spectrumTilt = 3.0f;
    public static volatile boolean absoluteVolume = true;

    private final SpectrumVisualizer visualizer = new SpectrumVisualizer(JSynFFT.FFT_SIZE, BAR_COUNT);
    private final float[] fftWindow = new float[JSynFFT.FFT_SIZE];
    private int fftWindowOffset;
    private int fftSamplesSinceAnalysis;

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
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        int frameCount = length / frameSize;
        float playbackVolume = volume;
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
            if (spectrumEnabled) {
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
        float[] magnitudes = FFT.analyzeSample(ordered, fftWindow.length);
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] *= 2.0f;
        }
        onFFT(magnitudes);
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
