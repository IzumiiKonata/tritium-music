package tritium.music.core.audio;

import tritium.music.core.util.HttpUtils;
import tritium.music.platform.Platform;
import tritium.music.repackage.javazoom.jl.decoder.*;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacAudioFileReader;
import tritium.music.repackage.org.kc7bfi.jflac.sound.spi.FlacFormatConversionProvider;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class StreamingSoundPlayer {
    private static final int MAX_STREAM_RETRIES = 3;
    private static final int MAX_CONSECUTIVE_INVALID_MP3_FRAMES = 32;
    private static final int PCM_UPDATE_MILLIS = 10;
    private static final int OUTPUT_BUFFER_MILLIS = 100;
    private static final int PREFETCH_BUFFER_BYTES = 8 * 1024 * 1024;
    private static final AtomicBoolean BEAT_THIS_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean BASIC_PITCH_FAILURE_LOGGED = new AtomicBoolean();
    private static final Semaphore AUTO_MIX_ANALYSIS_SLOT = new Semaphore(1);
    private final StreamFactory streamFactory;
    private final String type;
    private final long durationMillis;
    private final PcmListener pcmListener;
    private final Object pauseLock = new Object();
    private final AtomicLong requestedPositionMillis = new AtomicLong(-1);
    private final AtomicLong seekingPositionMillis = new AtomicLong(-1);
    private final CountDownLatch preparedLatch = new CountDownLatch(1);
    private volatile SourceDataLine line;
    private volatile InputStream input;
    private volatile Thread worker;
    private volatile boolean closed;
    private volatile boolean paused = true;
    private volatile boolean finished;
    private volatile long positionMillis;
    private volatile float volume = 0.25f;
    private volatile double playbackRate = 1;
    private volatile double pitchShiftSemitones;
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

    private static PcmChunk resample(byte[] data, int offset, int length, AudioFormat format, double rate) {
        if (Math.abs(rate - 1) < 0.0005) {
            return new PcmChunk(data, offset, length);
        }
        int frameSize = format.getFrameSize();
        int sourceFrames = length / frameSize;
        int outputFrames = Math.max(1, (int) Math.floor(sourceFrames / rate));
        byte[] output = new byte[outputFrames * frameSize];
        int bytesPerSample = frameSize / format.getChannels();
        long limit = (1L << (bytesPerSample * 8 - 1)) - 1;
        for (int frame = 0; frame < outputFrames; frame++) {
            double sourcePosition = Math.min(sourceFrames - 1, frame * rate);
            int leftFrame = (int) sourcePosition;
            int rightFrame = Math.min(sourceFrames - 1, leftFrame + 1);
            double fraction = sourcePosition - leftFrame;
            for (int channel = 0; channel < format.getChannels(); channel++) {
                int sampleOffset = channel * bytesPerSample;
                long left = readSignedSample(data, offset + leftFrame * frameSize + sampleOffset,
                        bytesPerSample, format.isBigEndian());
                long right = readSignedSample(data, offset + rightFrame * frameSize + sampleOffset,
                        bytesPerSample, format.isBigEndian());
                long sample = Math.max(-limit - 1, Math.min(limit, Math.round(left + (right - left) * fraction)));
                writeSignedSample(output, frame * frameSize + sampleOffset, bytesPerSample,
                        format.isBigEndian(), sample);
            }
        }
        return new PcmChunk(output, 0, output.length);
    }

    static byte[] resampleForAutoMix(byte[] data, AudioFormat format, double rate) {
        PcmChunk result = resample(data, 0, data.length, format, rate);
        return java.util.Arrays.copyOfRange(result.data(), result.offset(), result.offset() + result.length());
    }

    private static long readSignedSample(byte[] data, int offset, int bytes, boolean bigEndian) {
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
        return value << shift >> shift;
    }

    private static void writeSignedSample(byte[] data, int offset, int bytes, boolean bigEndian, long value) {
        long limit = (1L << (bytes * 8 - 1)) - 1;
        value = Math.max(-limit - 1, Math.min(limit, value));
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

    void play() {
        paused = false;
        SourceDataLine currentLine = line;
        if (currentLine != null) {
            currentLine.start();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        startWorker();
    }

    void prepare() {
        startWorker();
    }

    boolean awaitPrepared(long timeoutMillis) throws InterruptedException {
        return preparedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS) && !finished && !closed;
    }

    AutoMixTrackAnalysis analyzeForAutoMix(long startMillis, long maxMillis) throws IOException {
        boolean acquired = false;
        try {
            AUTO_MIX_ANALYSIS_SLOT.acquire();
            acquired = true;
            return analyzeForAutoMixLocked(startMillis, maxMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AutoMixTrackAnalysis.fallback(durationMillis);
        } finally {
            if (acquired) {
                AUTO_MIX_ANALYSIS_SLOT.release();
            }
        }
    }

    private AutoMixTrackAnalysis analyzeForAutoMixLocked(long startMillis, long maxMillis) throws IOException {
        AutoMixAnalyzer analyzer = new AutoMixAnalyzer(startMillis);
        BeatThisTempoAnalyzer tempoAnalyzer = new BeatThisTempoAnalyzer();
        try (InputStream opened = streamFactory.open();
             InputStream buffered = new BufferedInputStream(opened);
             PcmStream decoded = openPcmStream(buffered, type)) {
            PcmStream pcm = decoded;
            if (!Pcm16Stream.supports(decoded.format()) && decoded.format().getSampleSizeInBits() > 32) {
                return AutoMixTrackAnalysis.fallback(durationMillis);
            }
            byte[] buffer = new byte[32 * 1024];
            long skip = millisToBytes(startMillis, pcm.format());
            long limit = millisToBytes(maxMillis, pcm.format());
            long decodedBytes = 0;
            long analyzed = 0;
            while (analyzed < limit && !Thread.currentThread().isInterrupted()) {
                int read = pcm.read(buffer);
                if (read < 0) {
                    break;
                }
                if (decodedBytes + read <= skip) {
                    decodedBytes += read;
                    continue;
                }
                int offset = decodedBytes < skip ? (int) (skip - decodedBytes) : 0;
                decodedBytes += read;
                int length = (int) Math.min(read - offset, limit - analyzed);
                analyzer.accept(buffer, offset, length, pcm.format());
                tempoAnalyzer.accept(buffer, offset, length, pcm.format());
                analyzed += length;
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            return AutoMixTrackAnalysis.fallback(durationMillis);
        }
        AutoMixTrackAnalysis analysis = analyzer.trackAnalysis(durationMillis);
        if (Thread.currentThread().isInterrupted()) {
            return analysis;
        }
        try {
            BeatThisTempoAnalyzer.AudioAnalysis audioAnalysis = tempoAnalyzer.analyzeDetailed(startMillis);
            if (Thread.currentThread().isInterrupted()) {
                return analysis;
            }
            BeatThisTempoAnalyzer.BeatGrid beatGrid = audioAnalysis == null ? null : audioAnalysis.beatGrid();
            if (beatGrid != null && beatGrid.confidence() >= 0.16) {
                AutoMixTrackAnalysis enhanced = analysis.withProfile(analysis.profile().withBeatGrid(
                        beatGrid.intervalMillis(), beatGrid.phaseMillis(), beatGrid.confidence(),
                        beatGrid.downbeats() >= 2, beatGrid.downbeatIntervalMillis(),
                        beatGrid.downbeatPhaseMillis()));
                if (Thread.currentThread().isInterrupted()) {
                    return enhanced;
                }
                try {
                    MusicalTimeline timeline = new BasicPitchAnalyzer().analyze(
                            audioAnalysis.audio(), startMillis, beatGrid);
                    return enhanced.withTimeline(timeline);
                } catch (Throwable throwable) {
                    if (Thread.currentThread().isInterrupted()) {
                        return enhanced;
                    }
                    if (BASIC_PITCH_FAILURE_LOGGED.compareAndSet(false, true)) {
                        Platform.log("[NCM] Basic Pitch unavailable, using rhythm-only AutoMix: "
                                + throwable.getMessage());
                    }
                }
                return enhanced;
            }
        } catch (Throwable throwable) {
            if (Thread.currentThread().isInterrupted()) {
                return analysis;
            }
            if (BEAT_THIS_FAILURE_LOGGED.compareAndSet(false, true)) {
                Platform.log("[NCM] Beat This! unavailable, using AutoMix fallback: " + throwable.getMessage());
            }
        }
        return analysis;
    }

    private synchronized void startWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        finished = false;
        closed = false;
        worker = new Thread(this::run, "Music Stream Decoder");
        worker.setDaemon(true);
        worker.start();
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
    }

    void setPlaybackRate(double playbackRate) {
        this.playbackRate = Math.max(0.86, Math.min(playbackRate, 1.14));
    }

    void setPitchShiftSemitones(double pitchShiftSemitones) {
        this.pitchShiftSemitones = Math.max(-4, Math.min(pitchShiftSemitones, 4));
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
        return Math.min(durationMillis, positionMillis);
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
                    try (SoundTouchAudioProcessor soundTouch = SoundTouchAudioProcessor.create(pcm.format())) {
                        StreamingResampler streamingResampler = new StreamingResampler(pcm.format());
                        line = currentLine;
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
                            if (closed || requestedPositionMillis.get() >= 0) {
                                break;
                            }
                            int playable = read - offset;
                            int chunkSize = (int) Math.max(pcm.format().getFrameSize(),
                                    millisToBytes(PCM_UPDATE_MILLIS, pcm.format()));
                            int end = offset + playable;
                            while (offset < end && !closed && requestedPositionMillis.get() < 0) {
                                preparedLatch.countDown();
                                waitWhilePaused();
                                if (closed || requestedPositionMillis.get() >= 0) {
                                    break;
                                }
                                int length = Math.min(chunkSize, end - offset);
                                pcmListener.accept(buffer, offset, length, pcm.format());
                                PcmChunk output;
                                float outputGain = volume;
                                if (soundTouch.shouldProcess(playbackRate, pitchShiftSemitones)) {
                                    byte[] processed = soundTouch.process(buffer, offset, length,
                                            playbackRate, pitchShiftSemitones, outputGain);
                                    output = new PcmChunk(processed, 0, processed.length);
                                } else {
                                    output = streamingResampler.process(buffer, offset, length, playbackRate);
                                    applySoftwareVolume(output.data(), output.offset(), output.length(), pcm.format(), outputGain);
                                }
                                if (!writeFully(currentLine, output)) {
                                    continue;
                                }
                                offset += length;
                                seekingPositionMillis.compareAndSet(startMillis, -1);
                                positionMillis = Math.min(durationMillis, positionMillis
                                        + Math.round(length * 1000.0 / pcm.format().getFrameSize() / pcm.format().getFrameRate()));
                            }
                            streamFailures = 0;
                        }
                        if (!closed && requestedPositionMillis.get() < 0) {
                            byte[] tail = soundTouch.flush(volume);
                            writeFully(currentLine, new PcmChunk(tail, 0, tail.length));
                            PcmChunk resamplerTail = streamingResampler.flush();
                            applySoftwareVolume(resamplerTail.data(), resamplerTail.offset(),
                                    resamplerTail.length(), pcm.format(), volume);
                            writeFully(currentLine, resamplerTail);
                            preparedLatch.countDown();
                            currentLine.drain();
                            finished = true;
                            paused = true;
                            onFinished.run();
                            return;
                        }
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
                        preparedLatch.countDown();
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
        preparedLatch.countDown();
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

    private void applySoftwareVolume(byte[] data, int offset, int length, AudioFormat format, float gain) {
        if (gain >= 0.9999f || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return;
        }
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        if (bytesPerSample < 1 || bytesPerSample > 4) {
            return;
        }
        int end = offset + length - bytesPerSample + 1;
        for (int sampleOffset = offset; sampleOffset < end; sampleOffset += bytesPerSample) {
            long value = readSignedSample(data, sampleOffset, bytesPerSample, format.isBigEndian());
            writeSignedSample(data, sampleOffset, bytesPerSample, format.isBigEndian(), Math.round(value * gain));
        }
    }

    private boolean writeFully(SourceDataLine target, PcmChunk output) {
        int offset = output.offset();
        int remaining = output.length();
        while (remaining > 0 && !closed && requestedPositionMillis.get() < 0) {
            int written = target.write(output.data(), offset, remaining);
            if (written <= 0) {
                return false;
            }
            offset += written;
            remaining -= written;
        }
        return remaining == 0;
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

    record PcmChunk(byte[] data, int offset, int length) {
    }

    static final class StreamingResampler {
        private final AudioFormat format;
        private byte[] previousFrame;
        private double sourcePosition;
        private boolean active;

        StreamingResampler(AudioFormat format) {
            this.format = format;
        }

        PcmChunk process(byte[] data, int offset, int length, double rate) {
            int frameSize = format.getFrameSize();
            int currentFrames = length / frameSize;
            if (!active && Math.abs(rate - 1) < 0.0005) {
                return new PcmChunk(data, offset, currentFrames * frameSize);
            }
            active = true;
            int prefixFrames = previousFrame == null ? 0 : 1;
            int sourceFrames = prefixFrames + currentFrames;
            if (sourceFrames < 2) {
                previousFrame = Arrays.copyOfRange(data, offset, offset + currentFrames * frameSize);
                return new PcmChunk(new byte[0], 0, 0);
            }
            byte[] source = new byte[sourceFrames * frameSize];
            if (previousFrame != null) {
                System.arraycopy(previousFrame, 0, source, 0, frameSize);
            }
            System.arraycopy(data, offset, source, prefixFrames * frameSize, currentFrames * frameSize);
            int outputCapacity = Math.max(1, (int) Math.ceil((sourceFrames - 1 - sourcePosition) / rate));
            byte[] output = new byte[outputCapacity * frameSize];
            int outputFrames = 0;
            int bytesPerSample = frameSize / format.getChannels();
            long limit = (1L << (bytesPerSample * 8 - 1)) - 1;
            while (sourcePosition < sourceFrames - 1) {
                int leftFrame = (int) sourcePosition;
                int rightFrame = leftFrame + 1;
                double fraction = sourcePosition - leftFrame;
                for (int channel = 0; channel < format.getChannels(); channel++) {
                    int sampleOffset = channel * bytesPerSample;
                    long left = readSignedSample(source, leftFrame * frameSize + sampleOffset,
                            bytesPerSample, format.isBigEndian());
                    long right = readSignedSample(source, rightFrame * frameSize + sampleOffset,
                            bytesPerSample, format.isBigEndian());
                    long sample = Math.max(-limit - 1,
                            Math.min(limit, Math.round(left + (right - left) * fraction)));
                    writeSignedSample(output, outputFrames * frameSize + sampleOffset,
                            bytesPerSample, format.isBigEndian(), sample);
                }
                outputFrames++;
                sourcePosition += rate;
            }
            sourcePosition -= sourceFrames - 1;
            previousFrame = Arrays.copyOfRange(source, source.length - frameSize, source.length);
            return new PcmChunk(output, 0, outputFrames * frameSize);
        }

        PcmChunk flush() {
            if (!active || previousFrame == null) {
                return new PcmChunk(new byte[0], 0, 0);
            }
            byte[] output = previousFrame;
            previousFrame = null;
            return new PcmChunk(output, 0, output.length);
        }
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
