package tritium.music.core.audio;

import tritium.music.core.util.HttpUtils;
import tritium.music.repackage.javazoom.jl.decoder.Bitstream;
import tritium.music.repackage.javazoom.jl.decoder.BitstreamErrors;
import tritium.music.repackage.javazoom.jl.decoder.BitstreamException;
import tritium.music.repackage.javazoom.jl.decoder.Decoder;
import tritium.music.repackage.javazoom.jl.decoder.Header;
import tritium.music.repackage.javazoom.jl.decoder.SampleBuffer;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacAudioFileReader;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacFormatConversionProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class StreamingSoundPlayer {
    private static final int MAX_STREAM_RETRIES = 3;
    private static final int MAX_CONSECUTIVE_INVALID_MP3_FRAMES = 32;

    interface PcmListener {
        void accept(byte[] data, int offset, int length, AudioFormat format);
    }

    private interface StreamFactory {
        InputStream open() throws IOException;
    }

    private interface PcmStream extends Closeable {
        AudioFormat format();

        int read(byte[] buffer) throws IOException;
    }

    private final StreamFactory streamFactory;
    private final String type;
    private final long durationMillis;
    private final PcmListener pcmListener;
    private final Object pauseLock = new Object();
    private final AtomicLong requestedPositionMillis = new AtomicLong(-1);
    private final AtomicLong seekingPositionMillis = new AtomicLong(-1);

    private volatile SourceDataLine line;
    private volatile InputStream input;
    private volatile Thread worker;
    private volatile boolean closed;
    private volatile boolean paused = true;
    private volatile boolean finished;
    private volatile long positionMillis;
    private volatile long lineStartMillis;
    private volatile float volume = 0.25f;
    private volatile Runnable onFinished = () -> {
    };
    private volatile Runnable onFailed = () -> {
    };

    StreamingSoundPlayer(String url, String type, long durationMillis, PcmListener pcmListener) {
        this(() -> HttpUtils.get(url, null), type, durationMillis, pcmListener);
    }

    StreamingSoundPlayer(File file, long durationMillis, PcmListener pcmListener) {
        this(() -> Files.newInputStream(file.toPath()), extension(file), durationMillis, pcmListener);
    }

    private StreamingSoundPlayer(StreamFactory streamFactory, String type, long durationMillis, PcmListener pcmListener) {
        this.streamFactory = streamFactory;
        this.type = type.toLowerCase(Locale.ROOT);
        this.durationMillis = durationMillis;
        this.pcmListener = pcmListener;
    }

    void play() {
        paused = false;
        SourceDataLine currentLine = line;
        if (currentLine != null) {
            currentLine.start();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (worker == null || !worker.isAlive()) {
            finished = false;
            closed = false;
            worker = new Thread(this::run, "Music Stream Decoder");
            worker.setDaemon(true);
            worker.start();
        }
    }

    void pause() {
        paused = true;
        SourceDataLine currentLine = line;
        if (currentLine != null) {
            currentLine.stop();
        }
    }

    void seek(long millis) {
        long target = Math.max(0, Math.min(millis, durationMillis));
        seekingPositionMillis.set(target);
        requestedPositionMillis.set(target);
        positionMillis = target;
        closeInput();
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    void close() {
        closed = true;
        paused = false;
        seekingPositionMillis.set(-1);
        closeInput();
        SourceDataLine currentLine = line;
        if (currentLine != null) {
            currentLine.stop();
            currentLine.flush();
            currentLine.close();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    void setVolume(float volume) {
        this.volume = Math.max(0, Math.min(volume, 1));
        applyVolume(line);
    }

    void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    void setOnFailed(Runnable onFailed) {
        this.onFailed = onFailed;
    }

    boolean isPlaying() {
        return !paused && !finished && !closed;
    }

    boolean isFinished() {
        return finished;
    }

    long positionMillis() {
        long seekTarget = seekingPositionMillis.get();
        if (seekTarget >= 0) {
            return seekTarget;
        }
        SourceDataLine currentLine = line;
        if (currentLine == null || !currentLine.isOpen()) {
            return positionMillis;
        }
        return Math.min(durationMillis, lineStartMillis
                + (long) (currentLine.getLongFramePosition() * 1000 / currentLine.getFormat().getFrameRate()));
    }

    long durationMillis() {
        return durationMillis;
    }

    private void run() {
        long startMillis = positionMillis;
        int streamFailures = 0;
        while (!closed) {
            long requested = requestedPositionMillis.getAndSet(-1);
            if (requested >= 0) {
                startMillis = requested;
                positionMillis = requested;
            }

            try (InputStream opened = streamFactory.open()) {
                input = opened;
                try (PcmStream pcm = openPcmStream(new BufferedInputStream(opened), type)) {
                    SourceDataLine currentLine = openLine(pcm.format());
                    line = currentLine;
                    lineStartMillis = startMillis;
                    applyVolume(currentLine);
                    if (!paused) {
                        currentLine.start();
                    }
                    currentLine.flush();
                    long bytesToSkip = millisToBytes(startMillis, pcm.format());
                    byte[] buffer = new byte[32 * 1024];
                    long decodedBytes = 0;
                    int read;
                    while (!closed && (read = pcm.read(buffer)) >= 0) {
                        if (requestedPositionMillis.get() >= 0) {
                            break;
                        }
                        if (read == 0) {
                            continue;
                        }
                        if (decodedBytes + read <= bytesToSkip) {
                            decodedBytes += read;
                            continue;
                        }
                        int offset = 0;
                        if (decodedBytes < bytesToSkip) {
                            offset = (int) (bytesToSkip - decodedBytes);
                        }
                        decodedBytes += read;
                        waitWhilePaused();
                        if (closed || requestedPositionMillis.get() >= 0) {
                            break;
                        }
                        int playable = read - offset;
                        currentLine.write(buffer, offset, playable);
                        pcmListener.accept(buffer, offset, playable, pcm.format());
                        seekingPositionMillis.compareAndSet(startMillis, -1);
                        positionMillis = positionMillis();
                        streamFailures = 0;
                    }
                    if (!closed && requestedPositionMillis.get() < 0) {
                        currentLine.drain();
                        finished = true;
                        paused = true;
                        onFinished.run();
                        return;
                    }
                }
            } catch (Exception e) {
                if (!closed && requestedPositionMillis.get() < 0) {
                    e.printStackTrace();
                    seekingPositionMillis.set(-1);
                    startMillis = positionMillis();
                    positionMillis = startMillis;
                    streamFailures++;
                    if (streamFailures > MAX_STREAM_RETRIES) {
                        finished = true;
                        paused = true;
                        onFailed.run();
                        return;
                    }
                }
            } finally {
                positionMillis = positionMillis();
                input = null;
                SourceDataLine currentLine = line;
                line = null;
                if (currentLine != null) {
                    currentLine.close();
                }
            }
        }
    }

    private void waitWhilePaused() {
        synchronized (pauseLock) {
            while (paused && !closed && requestedPositionMillis.get() < 0) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    closed = true;
                }
            }
        }
    }

    private SourceDataLine openLine(AudioFormat format) throws LineUnavailableException {
        SourceDataLine result = AudioSystem.getSourceDataLine(format);
        result.open(format, Math.max(16 * 1024, (int) format.getFrameRate() * format.getFrameSize() / 2));
        return result;
    }

    private void applyVolume(SourceDataLine target) {
        if (target == null || !target.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) target.getControl(FloatControl.Type.MASTER_GAIN);
        float value = volume <= 0 ? gain.getMinimum() : (float) (20 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(value, gain.getMaximum())));
    }

    private void closeInput() {
        InputStream currentInput = input;
        if (currentInput != null) {
            try {
                currentInput.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static PcmStream openPcmStream(InputStream input, String type) throws IOException {
        return switch (type) {
            case "mp3" -> new Mp3PcmStream(input);
            case "flac" -> {
                try {
                    yield javaSound(new FlacAudioFileReader().getAudioInputStream(input), true);
                } catch (Exception e) {
                    throw new IOException("Invalid FLAC stream", e);
                }
            }
            case "wav" -> {
                try {
                    yield javaSound(AudioSystem.getAudioInputStream(input), false);
                } catch (Exception e) {
                    throw new IOException("Invalid WAV stream", e);
                }
            }
            default -> throw new IOException("Unsupported music format: " + type);
        };
    }

    private static PcmStream javaSound(AudioInputStream source, boolean flac) {
        AudioFormat sourceFormat = source.getFormat();
        int sampleSize = flac ? sourceFormat.getSampleSizeInBits() : 16;
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(),
                sampleSize, sourceFormat.getChannels(), sourceFormat.getChannels() * ((sampleSize + 7) / 8),
                sourceFormat.getSampleRate(), false);
        AudioInputStream pcm = flac
                ? new FlacFormatConversionProvider().getAudioInputStream(target, source)
                : AudioSystem.getAudioInputStream(target, source);
        return new JavaSoundPcmStream(pcm, target);
    }

    private static long millisToBytes(long millis, AudioFormat format) {
        long bytes = millis * (long) format.getFrameSize() * (long) format.getFrameRate() / 1000;
        return bytes - bytes % format.getFrameSize();
    }

    private static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private record JavaSoundPcmStream(AudioInputStream stream, AudioFormat format) implements PcmStream {
        @Override
        public int read(byte[] buffer) throws IOException {
            return stream.read(buffer);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private static final class Mp3PcmStream implements PcmStream {
        private final Bitstream bitstream;
        private final Decoder decoder = new Decoder();
        private AudioFormat format;
        private byte[] decoded = new byte[0];
        private int decodedOffset;

        private Mp3PcmStream(InputStream input) throws IOException {
            bitstream = new Bitstream(input);
            decodeNextFrame();
            if (format == null) {
                throw new IOException("Empty MP3 stream");
            }
        }

        @Override
        public AudioFormat format() {
            return format;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            if (decodedOffset >= decoded.length && !decodeNextFrame()) {
                return -1;
            }
            int length = Math.min(buffer.length, decoded.length - decodedOffset);
            System.arraycopy(decoded, decodedOffset, buffer, 0, length);
            decodedOffset += length;
            return length;
        }

        private boolean decodeNextFrame() throws IOException {
            int invalidFrames = 0;
            while (true) {
                Header header = null;
                try {
                    header = bitstream.readFrame();
                    if (header == null) {
                        return false;
                    }
                    SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    if (format == null) {
                        format = new AudioFormat(samples.getSampleFrequency(), 16, samples.getChannelCount(), true, false);
                    }
                    int sampleCount = samples.getBufferLength();
                    decoded = new byte[sampleCount * 2];
                    short[] source = samples.getBuffer();
                    for (int i = 0; i < sampleCount; i++) {
                        decoded[i * 2] = (byte) source[i];
                        decoded[i * 2 + 1] = (byte) (source[i] >>> 8);
                    }
                    decodedOffset = 0;
                    return true;
                } catch (BitstreamException e) {
                    if (e.getErrorCode() != BitstreamErrors.INVALIDFRAME
                            || ++invalidFrames > MAX_CONSECUTIVE_INVALID_MP3_FRAMES) {
                        throw new IOException("Failed to decode MP3 frame", e);
                    }
                    bitstream.closeFrame();
                } catch (Exception e) {
                    throw new IOException("Failed to decode MP3 frame", e);
                } finally {
                    if (header != null) {
                        bitstream.closeFrame();
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            try {
                bitstream.close();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
