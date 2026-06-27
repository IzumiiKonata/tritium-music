package tritium.music.core.audio;

import lombok.Getter;
import lombok.SneakyThrows;
import tritium.music.core.MusicState;
import tritium.music.repackage.processing.sound.FFT;
import tritium.music.repackage.processing.sound.JSynFFT;
import tritium.music.repackage.processing.sound.SoundFile;

import java.io.File;

public class AudioPlayer {

    public SoundFile player;
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

    private static int skipCount = 0;

    @Getter
    private final FFT fft = new FFT(BAR_COUNT, this::onFFT);
    private final SpectrumVisualizer visualizer = new SpectrumVisualizer(JSynFFT.FFT_SIZE, BAR_COUNT);

    @Getter
    public float volume = 0.25f;

    public AudioPlayer(File file) {
        finished = false;

        this.player = new SoundFile(file.getAbsolutePath());
        this.setListeners();
    }

    public void setAudio(File file) {
        this.close();

        this.player = new SoundFile(file.getAbsolutePath());
        this.setListeners();
        finished = false;
    }

    public void setListeners() {
        fft.removeInput();
        fft.input(this.player);
        player.setOnFinished(() -> finished = true);
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

    public void play() {
        finished = false;
        this.player.play();
        this.player.amp(volume);
    }

    @SneakyThrows
    public void setPlaybackTime(float millis) {
        this.player.jump(millis / 1000F);
        this.player.amp(volume);
    }

    @SneakyThrows
    public void close() {
        this.player.jump(0);
        player.stop();
        player.cleanUp();
    }

    @Getter
    private boolean finished;

    public void setAfterPlayed(Runnable runnable) {
        this.afterPlayed = runnable;
        this.player.setOnFinished(() -> {
            finished = true;
            runnable.run();
        });
    }

    public float getTotalTimeSeconds() {
        return (int) this.player.duration();
    }

    public float getCurrentTimeSeconds() {
        return (int) (getCurrentTimeMillis() / 1000);
    }

    public float getTotalTimeMillis() {
        return getTotalTimeSeconds() * 1000;
    }

    public float getCurrentTimeMillis() {
        return this.player.position() * 1000;
    }

    public boolean isPausing() {
        return !this.player.isPlaying();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        MusicState.get().setVolume(volume);
        this.player.amp(this.getVolume());
    }

    public void pause() {
        this.player.pause();
    }

    public void unpause() {
        this.play();
    }
}
