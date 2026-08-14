package tritium.music.core.audio;

import tritium.music.core.util.HttpUtils;
import tritium.music.repackage.javazoom.jl.decoder.*;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacAudioFileReader;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacFormatConversionProvider;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class StreamingSoundPlayer {
    private static final int MAX_STREAM_RETRIES = 3;
    private static final int MAX_CONSECUTIVE_INVALID_MP3_FRAMES = 32;
    private static final int PCM_UPDATE_MILLIS = 10;
    private static final int OUTPUT_BUFFER_MILLIS = 100;
    private static final int PREFETCH_BUFFER_BYTES = 8 * 1024 * 1024;

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

            try (InputStream opened = streamFactory.open();
                 InputStream prefetched = new PrefetchInputStream(opened, PREFETCH_BUFFER_BYTES)) {
                input = prefetched;
                try (PcmStream decoded = openPcmStream(new BufferedInputStream(prefetched), type)) {
                    PcmStream pcm = decoded;
                    SourceDataLine currentLine;
                    try {
                        currentLine = openLine(pcm.format());
                    } catch (IllegalArgumentException e) {
                        if (!Pcm16Stream.supports(pcm.format())) {
                            throw e;
                        }
                        pcm = new Pcm16Stream(pcm);
                        currentLine = openLine(pcm.format());
                    }
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
                        int chunkSize = (int) Math.max(pcm.format().getFrameSize(),
                                millisToBytes(PCM_UPDATE_MILLIS, pcm.format()));
                        int end = offset + playable;
                        while (offset < end && !closed && requestedPositionMillis.get() < 0) {
                            waitWhilePaused();
                            if (closed || requestedPositionMillis.get() >= 0) {
                                break;
                            }
                            int length = Math.min(chunkSize, end - offset);
                            int written = currentLine.write(buffer, offset, length);
                            if (written <= 0) {
                                continue;
                            }
                            pcmListener.accept(buffer, offset, written, pcm.format());
                            offset += written;
                            seekingPositionMillis.compareAndSet(startMillis, -1);
                            positionMillis = positionMillis();
                        }
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
        result.open(format, (int) Math.max(16 * 1024, millisToBytes(OUTPUT_BUFFER_MILLIS, format)));
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

    private static final class Pcm16Stream implements PcmStream {
        private final PcmStream source;
        private final AudioFormat sourceFormat;
        private final AudioFormat format;
        private byte[] sourceBuffer = new byte[0];

        private Pcm16Stream(PcmStream source) {
            this.source = source;
            sourceFormat = source.format();
            int channels = sourceFormat.getChannels();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16,
                    channels, channels * 2, sourceFormat.getFrameRate(), false);
        }

        private static boolean supports(AudioFormat format) {
            int sampleSize = format.getSampleSizeInBits();
            int channels = format.getChannels();
            return format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    && sampleSize > 16
                    && sampleSize <= 32
                    && sampleSize % 8 == 0
                    && channels > 0
                    && format.getFrameSize() == channels * sampleSize / 8;
        }

        @Override
        public AudioFormat format() {
            return format;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            int outputFrameSize = format.getFrameSize();
            int frameCapacity = buffer.length / outputFrameSize;
            if (frameCapacity == 0) {
                return 0;
            }
            int sourceLength = frameCapacity * sourceFormat.getFrameSize();
            if (sourceBuffer.length != sourceLength) {
                sourceBuffer = new byte[sourceLength];
            }
            int read = source.read(sourceBuffer);
            if (read <= 0) {
                return read;
            }
            int sourceFrameSize = sourceFormat.getFrameSize();
            int sourceBytesPerSample = sourceFormat.getSampleSizeInBits() / 8;
            int frames = read / sourceFrameSize;
            int outputOffset = 0;
            for (int frame = 0; frame < frames; frame++) {
                int sourceFrameOffset = frame * sourceFrameSize;
                for (int channel = 0; channel < sourceFormat.getChannels(); channel++) {
                    int sourceOffset = sourceFrameOffset + channel * sourceBytesPerSample;
                    int sample = readSample(sourceBuffer, sourceOffset, sourceBytesPerSample,
                            sourceFormat.isBigEndian());
                    sample >>= sourceFormat.getSampleSizeInBits() - 16;
                    buffer[outputOffset++] = (byte) sample;
                    buffer[outputOffset++] = (byte) (sample >>> 8);
                }
            }
            return outputOffset;
        }

        private static int readSample(byte[] data, int offset, int bytes, boolean bigEndian) {
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
            return value << shift >> shift;
        }

        @Override
        public void close() throws IOException {
            source.close();
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
