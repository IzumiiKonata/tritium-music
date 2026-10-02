package tritium.music.client.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import tritium.music.platform.PcmOutput;
import tritium.music.platform.Platform;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenALPCMOutput implements PcmOutput {

    private static final int BUFFER_MILLIS = 20;
    private static final int MIN_IN_FLIGHT = 10;
    private static final int MAX_IN_FLIGHT = 20;
    private static final long CUSHION_BYTES = 320L * 1024L;
    private static final long STALL_NANOS = 3_000_000_000L;
    private static final long RECOVERY_COOLDOWN_NANOS = 8_000_000_000L;
    private static final long RETRY_NANOS = 10_000_000_000L;
    private static final long RECOVERY_WINDOW_NANOS = 60_000_000_000L;
    private static final long CONTEXT_WAIT_NANOS = 2_000_000_000L;
    private static final int MAX_RECOVERIES = 3;
    private static final int VERIFY_POLLS = 12;
    private static final int OFFSET_SAMPLES = 0;
    private static final int OFFSET_SECONDS = 1;
    private static final int OFFSET_NONE = 2;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private final Object lock = new Object();
    private final AudioFormat format;
    private final int alFormat;
    private final int sampleRate;
    private final int frameSize;
    private final byte[] pending;
    private final ByteBuffer upload;
    private final int[] bufferIds;
    private final int[] queueFrames;

    private int queueCount;
    private int queueHead;
    private int nextBuffer;
    private int pendingBytes;
    private int offsetMode = OFFSET_SAMPLES;
    private long queuedFrames;
    private long unqueuedFrames;
    private long deviceGeneration;
    private int underruns;
    private long recoveryWindowStart;
    private long lastRecoveryNanos;
    private int recoveries;
    private boolean draining;
    private boolean started;
    private boolean observedPlaying;
    private boolean failed;
    private volatile int source;
    private volatile long playedFrames;
    private volatile boolean playing;
    private volatile boolean closed;

    private OpenALPCMOutput(AudioFormat format, int alFormat, int bufferFrames, int inFlight) {
        this.format = format;
        this.alFormat = alFormat;
        this.sampleRate = (int) format.getSampleRate();
        this.frameSize = format.getFrameSize();
        this.pending = new byte[bufferFrames * frameSize];
        this.upload = BufferUtils.createByteBuffer(pending.length);
        this.bufferIds = new int[inFlight];
        this.queueFrames = new int[inFlight];
    }

    public static PcmOutput open(AudioFormat format, int bufferBytes) {
        int alFormat = alFormat(format);
        int frameSize = format.getFrameSize();
        int frames = Math.max(1, Math.round(format.getSampleRate() * BUFFER_MILLIS / 1000.0f));
        int bufferLength = frames * frameSize;
        long cushion = Math.max(bufferBytes, CUSHION_BYTES);
        int inFlight = (int) Math.max(MIN_IN_FLIGHT, Math.min(MAX_IN_FLIGHT, cushion / bufferLength));

        long deadline = System.nanoTime() + CONTEXT_WAIT_NANOS;
        while (true) {
            if (OpenALContext.live() == 0) {
                if (System.nanoTime() >= deadline || !sleep(20L)) {
                    return null;
                }
                continue;
            }

            OpenALPCMOutput output = new OpenALPCMOutput(format, alFormat, frames, inFlight);
            try {
                if (output.initialize()) {
                    return output;
                }
            } catch (Throwable throwable) {
                report("startup", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            output.closed = true;
            output.release();
            if (OpenALContext.live() != 0) {
                return null;
            }
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        int dropped = 0;
        synchronized (lock) {
            if (!closed) {
                retryAfterFailure();
            }
            if (!syncDevice()) {
                dropped = length;
            } else {
                int written = 0;
                while (written < length) {
                    int chunk = Math.min(pending.length - pendingBytes, length - written);
                    if (chunk <= 0) {
                        submit();
                        continue;
                    }
                    System.arraycopy(data, offset + written, pending, pendingBytes, chunk);
                    pendingBytes += chunk;
                    written += chunk;
                    if (pendingBytes == pending.length) {
                        submit();
                    }
                    if (inactive()) {
                        advance(pendingBytes / frameSize);
                        pendingBytes = 0;
                        break;
                    }
                }
                if (inactive()) {
                    dropped = length - written;
                } else {
                    refreshPosition();
                }
            }
        }
        if (dropped > 0) {
            drop(dropped);
        }
        return length;
    }

    @Override
    public long framePosition() {
        return playedFrames;
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            retryAfterFailure();
            playing = true;
            if (syncDevice()) {
                if (!started && queueCount >= startThreshold()) {
                    started = true;
                }
                ensurePlaying();
            }
            lock.notifyAll();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            playing = false;
            if (syncDevice() && state() == AL10.AL_PLAYING) {
                AL10.alSourcePause(source);
                refreshPosition();
            }
            lock.notifyAll();
        }
    }

    @Override
    public void flush() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            pendingBytes = 0;
            if (syncDevice()) {
                unqueueProcessed();
                ensurePlaying();
            }
            lock.notifyAll();
        }
    }

    @Override
    public void drain() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            draining = true;
            try {
                submit();
                while (!closed && playing && syncDevice() && queueCount > 0) {
                    refreshPosition();
                    if (queueCount == 0) {
                        break;
                    }
                    ensurePlaying();
                    try {
                        lock.wait(5);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                refreshPosition();
            } finally {
                draining = false;
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            playing = false;
            release();
            resetQueue();
            lock.notifyAll();
        }
    }

    private boolean initialize() {
        deviceGeneration = OpenALContext.generation();
        if (deviceGeneration == 0) {
            return false;
        }
        drainErrors("startup");
        if (!createObjects()) {
            return false;
        }
        if (!verify()) {
            return false;
        }
        drainErrors("initialization");
        return true;
    }

    private boolean createObjects() {
        int generated = AL10.alGenSources();
        if (generated == 0) {
            report("alGenSources", codeName(AL10.alGetError()));
            return false;
        }
        source = generated;
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_GAIN, 1.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
        AL10.alSource3f(source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        for (int index = 0; index < bufferIds.length; index++) {
            bufferIds[index] = AL10.alGenBuffers();
            if (bufferIds[index] == 0) {
                report("alGenBuffers", codeName(AL10.alGetError()));
                return false;
            }
        }
        return true;
    }

    private boolean verify() {
        byte[] silence = new byte[pending.length];
        upload.clear();
        upload.put(silence, 0, silence.length);
        upload.flip();

        int id = bufferIds[0];
        AL10.alBufferData(id, alFormat, upload, sampleRate);
        AL10.alSourceQueueBuffers(source, id);
        AL10.alSourcePlay(source);

        boolean playing = false;
        for (int attempt = 0; attempt < VERIFY_POLLS; attempt++) {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                playing = true;
                break;
            }
            if (!sleep(2L)) {
                break;
            }
        }

        AL10.alSourceStop(source);
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            AL10.alSourceUnqueueBuffers(source);
        }
        if (!playing) {
            report("playback probe", "the source never reached AL_PLAYING");
        }

        drainErrors("verification");
        deleteObjects();
        resetQueue();
        return playing && createObjects();
    }

    private boolean syncDevice() {
        long observed = OpenALContext.generation();
        if (observed == deviceGeneration) {
            return source != 0 && !failed;
        }

        deviceGeneration = observed;
        abandon();
        if (observed == 0) {
            log("the Minecraft OpenAL context is gone, waiting for an audio device");
            return false;
        }
        if (!createObjects()) {
            return false;
        }

        failed = false;
        recoveries = 0;
        recoveryWindowStart = 0L;
        underruns = 0;
        started = false;
        observedPlaying = false;
        log("moved the OpenAL output onto the audio device Minecraft switched to");
        return true;
    }

    private void abandon() {
        long lost = queuedFrames + pendingBytes / frameSize;
        source = 0;
        Arrays.fill(bufferIds, 0);
        queueCount = 0;
        queueHead = 0;
        nextBuffer = 0;
        queuedFrames = 0;
        started = false;
        observedPlaying = false;
        advance((int) Math.min(Integer.MAX_VALUE, lost));
    }

    private void release() {
        try {
            if (deviceGeneration != 0 && OpenALContext.generation() == deviceGeneration) {
                deleteObjects();
            } else {
                abandon();
            }
            drainErrors("cleanup");
        } catch (Throwable throwable) {
            log("OpenAL cleanup failed: " + throwable);
        }
    }

    private void deleteObjects() {
        int currentSource = source;
        source = 0;
        if (currentSource != 0) {
            AL10.alSourceStop(currentSource);
            AL10.alDeleteSources(currentSource);
        }
        for (int index = 0; index < bufferIds.length; index++) {
            if (bufferIds[index] != 0) {
                AL10.alDeleteBuffers(bufferIds[index]);
                bufferIds[index] = 0;
            }
        }
    }

    private void submit() {
        if (pendingBytes == 0) {
            return;
        }
        if (!syncDevice() || !awaitSlot()) {
            advance(pendingBytes / frameSize);
            pendingBytes = 0;
            return;
        }

        int frames = pendingBytes / frameSize;
        for (int attempt = 0; attempt < 2; attempt++) {
            int queued = pendingBytes;
            int id = bufferIds[nextBuffer];
            upload.clear();
            upload.put(pending, 0, queued);
            upload.flip();

            int before = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            AL10.alBufferData(id, alFormat, upload, sampleRate);
            AL10.alSourceQueueBuffers(source, id);
            if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) <= before) {
                report("alSourceQueueBuffers", codeName(AL10.alGetError()));
                if (attempt == 0 && recover()) {
                    pendingBytes = queued;
                    continue;
                }
                break;
            }

            nextBuffer = (nextBuffer + 1) % bufferIds.length;
            int slot = (queueHead + queueCount) % queueFrames.length;
            queueFrames[slot] = frames;
            queueCount++;
            queuedFrames += frames;
            pendingBytes = 0;

            if (playing && !started && (queueCount >= startThreshold() || draining)) {
                started = true;
            }
            ensurePlaying();
            return;
        }

        advance(frames);
        pendingBytes = 0;
    }

    private boolean awaitSlot() {
        long stalledSince = 0L;
        while (!inactive() && (queueCount >= bufferIds.length || (!playing && queueCount >= 1))) {
            if (!syncDevice()) {
                return false;
            }
            refreshPosition();
            if (inactive()) {
                return false;
            }
            if (queueCount < bufferIds.length && (playing || queueCount < 1)) {
                return true;
            }
            ensurePlaying();

            if (playing && started && queueCount >= bufferIds.length) {
                long now = System.nanoTime();
                if (stalledSince == 0L) {
                    stalledSince = now;
                } else if (now - stalledSince >= STALL_NANOS) {
                    stalledSince = 0L;
                    if (!recover()) {
                        return false;
                    }
                    return true;
                }
            } else {
                stalledSince = 0L;
            }

            try {
                lock.wait(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closed = true;
                return false;
            }
        }
        return !inactive();
    }

    private boolean recover() {
        long now = System.nanoTime();
        if (recoveries > 0 && now - lastRecoveryNanos < RECOVERY_COOLDOWN_NANOS) {
            return false;
        }
        lastRecoveryNanos = now;
        if (now - recoveryWindowStart > RECOVERY_WINDOW_NANOS) {
            recoveryWindowStart = now;
            recoveries = 0;
        }
        recoveries++;
        if (recoveries > MAX_RECOVERIES) {
            failed = true;
            log("OpenAL stopped consuming audio, dropping audio until the device recovers");
            return false;
        }

        long lostMillis = Math.round((queuedFrames + pendingBytes / (double) frameSize) * 1000.0 / sampleRate);
        log("OpenAL output stalled, rebuilding the stream and dropping " + lostMillis + " ms of buffered audio (attempt " + recoveries + "/" + MAX_RECOVERIES + ")");
        deleteObjects();
        resetQueue();
        started = false;
        observedPlaying = false;
        if (!createObjects()) {
            failed = true;
            return false;
        }
        failed = false;
        return true;
    }

    private void retryAfterFailure() {
        if (!failed) {
            return;
        }
        if (System.nanoTime() - lastRecoveryNanos < RETRY_NANOS) {
            return;
        }
        if (OpenALContext.generation() != deviceGeneration) {
            return;
        }
        recoveryWindowStart = 0L;
        recoveries = 0;
        if (recover()) {
            log("OpenAL output recovered, resuming playback");
        }
    }

    private void ensurePlaying() {
        if (!playing || source == 0 || failed) {
            return;
        }
        if (!started) {
            if (queueCount < startThreshold()) {
                return;
            }
            started = true;
        }
        int current = state();
        if (current == AL10.AL_PLAYING || current == AL10.AL_PAUSED) {
            return;
        }
        AL10.alSourcePlay(source);
        int code = AL10.alGetError();
        if (code != AL10.AL_NO_ERROR) {
            report("alSourcePlay", codeName(code));
        }
    }

    private void refreshPosition() {
        if (source == 0) {
            return;
        }
        int currentState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (!draining && playing && started && observedPlaying && queueCount > 0 && currentState == AL10.AL_STOPPED) {
            underruns++;
            if (underruns == 1) {
                log("OpenAL buffer underrun detected, the output is not keeping up");
            }
        }

        unqueueProcessed();

        if (currentState == AL10.AL_PLAYING) {
            observedPlaying = true;
        }
        if (queueCount == 0) {
            observedPlaying = false;
        }

        long offset = 0;
        if (currentState == AL10.AL_PLAYING || currentState == AL10.AL_PAUSED) {
            offset = sampleOffset();
            if (offset < 0) {
                offset = 0;
            } else if (offset > queuedFrames) {
                offset = queuedFrames;
            }
        }

        long position = unqueuedFrames + offset;
        if (position > playedFrames) {
            playedFrames = position;
        }
        drainErrors("position");
    }

    private long sampleOffset() {
        if (offsetMode == OFFSET_SAMPLES) {
            long samples = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                return samples;
            }
            offsetMode = OFFSET_SECONDS;
        }
        if (offsetMode == OFFSET_SECONDS) {
            float seconds = AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
            if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                return Math.round(seconds * sampleRate);
            }
            offsetMode = OFFSET_NONE;
        }
        return 0;
    }

    private void unqueueProcessed() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0 && queueCount > 0) {
            AL10.alSourceUnqueueBuffers(source);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                return;
            }
            int frames = queueFrames[queueHead];
            unqueuedFrames += frames;
            queuedFrames -= frames;
            queueHead = (queueHead + 1) % queueFrames.length;
            queueCount--;
        }
    }

    private void resetQueue() {
        queueCount = 0;
        queueHead = 0;
        nextBuffer = 0;
        pendingBytes = 0;
        queuedFrames = 0;
    }

    private void drop(int length) {
        int frames = Math.max(0, length) / frameSize;
        if (frames <= 0) {
            return;
        }
        synchronized (lock) {
            advance(frames);
        }
        long millis = Math.round(frames * 1000.0 / sampleRate);
        if (millis <= 0) {
            return;
        }
        if (!sleep(millis)) {
            closed = true;
        }
    }

    private void advance(int frames) {
        if (frames <= 0) {
            return;
        }
        unqueuedFrames += frames;
        if (unqueuedFrames > playedFrames) {
            playedFrames = unqueuedFrames;
        }
    }

    private boolean inactive() {
        return closed || failed || source == 0;
    }

    private int startThreshold() {
        return Math.max(2, Math.min(4, bufferIds.length / 4));
    }

    private int state() {
        return source == 0 ? AL10.AL_STOPPED : AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void drainErrors(String operation) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int error = AL10.alGetError();
            if (error == AL10.AL_NO_ERROR) {
                return;
            }
            report(operation, codeName(error));
        }
    }

    private static void report(String operation, String detail) {
        if (REPORTED.add(operation + '|' + detail)) {
            log("OpenAL " + operation + ": " + detail);
        }
    }

    private static String codeName(int code) {
        return switch (code) {
            case AL10.AL_NO_ERROR -> "AL_NO_ERROR";
            case AL10.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL10.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL10.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL10.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL10.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(code);
        };
    }

    private static int alFormat(AudioFormat format) {
        int channels = format.getChannels();
        int sampleSize = format.getSampleSizeInBits();
        boolean bigEndian = format.isBigEndian();
        boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
        boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
        if (signed && sampleSize == 16 && !bigEndian) {
            if (channels == 1) {
                return AL10.AL_FORMAT_MONO16;
            }
            if (channels == 2) {
                return AL10.AL_FORMAT_STEREO16;
            }
        }
        if (unsigned && sampleSize == 8) {
            if (channels == 1) {
                return AL10.AL_FORMAT_MONO8;
            }
            if (channels == 2) {
                return AL10.AL_FORMAT_STEREO8;
            }
        }
        throw new IllegalArgumentException("Unsupported OpenAL format: " + format);
    }

    private static void log(String message) {
        try {
            Platform.log("[NCM] " + message);
        } catch (Throwable ignored) {
        }
    }
}
