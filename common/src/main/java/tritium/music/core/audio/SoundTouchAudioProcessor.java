package tritium.music.core.audio;

import com.tianscar.soundtouch.SoundTouch;
import tritium.music.platform.Platform;

import javax.sound.sampled.AudioFormat;
import java.util.concurrent.atomic.AtomicBoolean;

final class SoundTouchAudioProcessor implements AutoCloseable {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile String failureMessage = "";

    private final SoundTouch soundTouch;
    private final AudioFormat format;
    private final int channels;
    private boolean active;
    private float tempo = -1;
    private float pitchSemitones = -100;

    private SoundTouchAudioProcessor(SoundTouch soundTouch, AudioFormat format) {
        this.soundTouch = soundTouch;
        this.format = format;
        channels = format.getChannels();
    }

    static SoundTouchAudioProcessor create(AudioFormat format) {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.getSampleSizeInBits() != 16 || format.getFrameSize() != format.getChannels() * 2) {
            return new SoundTouchAudioProcessor(null, format);
        }
        try {
            SoundTouchNativeLoader.load();
            SoundTouch soundTouch = new SoundTouch();
            soundTouch.setSampleRate(Math.round(format.getSampleRate()));
            soundTouch.setChannels(format.getChannels());
            soundTouch.setSetting(SoundTouch.SETTING_USE_QUICKSEEK, 0);
            soundTouch.setSetting(SoundTouch.SETTING_USE_AA_FILTER, 1);
            return new SoundTouchAudioProcessor(soundTouch, format);
        } catch (Throwable throwable) {
            failureMessage = throwable.toString();
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                try {
                    Platform.log("[NCM] SoundTouch unavailable, using rate-only fallback: " + throwable.getMessage());
                } catch (IllegalStateException ignored) {
                }
            }
            return new SoundTouchAudioProcessor(null, format);
        }
    }

    static String failureMessage() {
        return failureMessage;
    }

    boolean isAvailable() {
        return soundTouch != null;
    }

    boolean shouldProcess(double requestedTempo, double requestedPitchSemitones) {
        if (soundTouch == null) {
            return false;
        }
        if (Math.abs(requestedTempo - 1) >= 0.0005 || Math.abs(requestedPitchSemitones) >= 0.001) {
            active = true;
        }
        return active;
    }

    byte[] process(byte[] data, int offset, int length, double requestedTempo,
                   double requestedPitchSemitones, float outputGain) {
        if (soundTouch == null) {
            return new byte[0];
        }
        updateParameters(requestedTempo, requestedPitchSemitones);
        int frames = length / format.getFrameSize();
        float[] input = new float[frames * channels];
        for (int sample = 0; sample < input.length; sample++) {
            int sampleOffset = offset + sample * 2;
            short value = (short) (format.isBigEndian() ? data[sampleOffset] << 8 | data[sampleOffset + 1] & 0xff : data[sampleOffset] & 0xff | data[sampleOffset + 1] << 8);
            input[sample] = value / 32768f;
        }
        soundTouch.putSamples(input, 0, frames);
        return receive(outputGain);
    }

    byte[] flush(float outputGain) {
        if (soundTouch == null) {
            return new byte[0];
        }
        soundTouch.flush();
        return receive(outputGain);
    }

    private void updateParameters(double requestedTempo, double requestedPitchSemitones) {
        float nextTempo = (float) Math.max(0.86, Math.min(1.14, requestedTempo));
        float nextPitch = (float) Math.max(-4, Math.min(4, requestedPitchSemitones));
        if (Math.abs(nextTempo - tempo) > 0.0001f || Math.abs(nextPitch - pitchSemitones) > 0.001f) {
            float pitchRatio = (float) Math.pow(2, nextPitch / 12.0);
            soundTouch.setRate(pitchRatio);
            soundTouch.setTempo(nextTempo / pitchRatio);
            tempo = nextTempo;
            pitchSemitones = nextPitch;
        }
    }

    private byte[] receive(float outputGain) {
        int availableFrames = Math.toIntExact(Math.min(Integer.MAX_VALUE, soundTouch.numSamples()));
        if (availableFrames == 0) {
            return new byte[0];
        }
        float[] output = new float[availableFrames * channels];
        int receivedFrames = soundTouch.receiveSamples(output, 0, availableFrames);
        byte[] bytes = new byte[receivedFrames * channels * 2];
        for (int sample = 0; sample < receivedFrames * channels; sample++) {
            double scaled = output[sample] * Math.max(0, Math.min(1, outputGain));
            short value = (short) Math.round(Math.max(-1, Math.min(0.999969, scaled)) * 32768);
            int offset = sample * 2;
            if (format.isBigEndian()) {
                bytes[offset] = (byte) (value >>> 8);
                bytes[offset + 1] = (byte) value;
            } else {
                bytes[offset] = (byte) value;
                bytes[offset + 1] = (byte) (value >>> 8);
            }
        }
        return bytes;
    }

    @Override
    public void close() {
        if (soundTouch != null && !soundTouch.isDisposed()) {
            soundTouch.dispose();
        }
    }
}
