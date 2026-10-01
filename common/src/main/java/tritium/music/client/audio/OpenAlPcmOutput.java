package tritium.music.client.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import tritium.music.platform.PcmOutput;
import tritium.music.platform.Platform;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public final class OpenAlPcmOutput implements PcmOutput {

    private static final int MIN_BUFFER_MILLIS = 40;
    private static final int MIN_IN_FLIGHT = 3;
    private static final int MAX_IN_FLIGHT = 8;

    private final Object lock = new Object();
    private final AudioFormat format;
    private final int alFormat;
    private final int sampleRate;
    private final int frameSize;
    private final byte[] pending;
    private final ByteBuffer upload;
    private final int[] bufferIds;
    private final int[] queueIds;
    private final int[] queueFrames;
    private final int[] scratch = new int[1];

    private int queueCount;
    private int queueHead;
    private int nextBuffer;
    private int pendingBytes;
    private long queuedFrames;
    private long unqueuedFrames;
    private boolean failed;
    private volatile int source;
    private volatile long playedFrames;
    private volatile boolean playing;
    private volatile boolean closed;

    private OpenAlPcmOutput(AudioFormat format, int alFormat, int bufferFrames, int inFlight) {
        this.format = format;
        this.alFormat = alFormat;
        this.sampleRate = (int) format.getSampleRate();
        this.frameSize = format.getFrameSize();
        this.pending = new byte[bufferFrames * frameSize];
        this.upload = BufferUtils.createByteBuffer(pending.length);
        this.bufferIds = new int[inFlight];
        this.queueIds = new int[inFlight];
        this.queueFrames = new int[inFlight];
    }

    public static PcmOutput open(AudioFormat format, int bufferBytes) {
        int alFormat = alFormat(format);
        int frameSize = format.getFrameSize();
        int frames = Math.max(1, Math.round(format.getSampleRate() * MIN_BUFFER_MILLIS / 1000.0f));
        int bufferLength = frames * frameSize;
        int inFlight = Math.max(MIN_IN_FLIGHT, Math.min(MAX_IN_FLIGHT, bufferBytes / bufferLength));

        OpenAlPcmOutput output = new OpenAlPcmOutput(format, alFormat, frames, inFlight);
        try {
            if (output.initialize()) {
                return output;
            }
        } catch (Throwable throwable) {
            log("OpenAL output unavailable: " + throwable);
        }
        output.release();
        output.closed = true;
        return null;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        int dropped = -1;
        synchronized (lock) {
            if (closed || failed) {
                dropped = length;
            } else {
                int written = 0;
                while (written < length) {
                    int chunk = Math.min(pending.length - pendingBytes, length - written);
                    System.arraycopy(data, offset + written, pending, pendingBytes, chunk);
                    pendingBytes += chunk;
                    written += chunk;
                    if (pendingBytes == pending.length) {
                        submit();
                    }
                    if (closed || failed) {
                        break;
                    }
                }
                if (closed || failed) {
                    dropped = length - written;
                } else {
                    refreshPosition();
                    return written;
                }
            }
        }
        drop(dropped);
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
            playing = true;
            if (!failed && state() != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
                clearErrors();
                refreshPosition();
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
            if (!failed && state() == AL10.AL_PLAYING) {
                AL10.alSourcePause(source);
                refreshPosition();
            }
        }
    }

    @Override
    public void flush() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            release();
            resetQueue();
            if (!initialize()) {
                fail();
                return;
            }
            if (playing) {
                AL10.alSourcePlay(source);
                clearErrors();
            }
        }
    }

    @Override
    public void drain() {
        synchronized (lock) {
            if (closed || failed) {
                return;
            }
            submit();
            while (!closed && !failed && playing && queueCount > 0) {
                refreshPosition();
                if (queueCount == 0) {
                    break;
                }
                try {
                    lock.wait(5);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            refreshPosition();
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
        clearErrors();
        int generated = AL10.alGenSources();
        if (generated == 0 || clearErrors()) {
            return false;
        }
        source = generated;
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_GAIN, 1.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
        AL10.alSource3f(source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        AL10.alGenBuffers(bufferIds);
        for (int id : bufferIds) {
            if (id == 0) {
                return false;
            }
        }
        return !clearErrors();
    }

    private void release() {
        try {
            int currentSource = source;
            source = 0;
            if (currentSource != 0) {
                AL10.alSourceStop(currentSource);
                AL10.alDeleteSources(new int[]{currentSource});
            }
            for (int index = 0; index < bufferIds.length; index++) {
                if (bufferIds[index] != 0) {
                    AL10.alDeleteBuffers(new int[]{bufferIds[index]});
                    bufferIds[index] = 0;
                }
            }
            clearErrors();
        } catch (Throwable throwable) {
            log("OpenAL cleanup failed: " + throwable);
        }
    }

    private void submit() {
        awaitSlot();
        if (closed || failed || pendingBytes == 0) {
            pendingBytes = 0;
            return;
        }
        int id = bufferIds[nextBuffer];
        nextBuffer = (nextBuffer + 1) % bufferIds.length;
        upload.clear();
        upload.put(pending, 0, pendingBytes);
        upload.flip();
        int frames = pendingBytes / frameSize;
        AL10.alBufferData(id, alFormat, upload, sampleRate);
        scratch[0] = id;
        AL10.alSourceQueueBuffers(source, scratch);
        int slot = (queueHead + queueCount) % queueIds.length;
        queueIds[slot] = id;
        queueFrames[slot] = frames;
        queueCount++;
        queuedFrames += frames;
        pendingBytes = 0;
        if (playing && (state() == AL10.AL_STOPPED || state() == AL10.AL_INITIAL)) {
            AL10.alSourcePlay(source);
        }
        if (clearErrors()) {
            fail();
        }
    }

    private void awaitSlot() {
        while (!closed && !failed && (queueCount >= bufferIds.length || (!playing && queueCount >= 1))) {
            refreshPosition();
            try {
                lock.wait(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closed = true;
                return;
            }
        }
    }

    private void refreshPosition() {
        if (source == 0 || failed) {
            return;
        }
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0 && queueCount > 0) {
            AL10.alSourceUnqueueBuffers(source, scratch);
            int frames = queueFrames[queueHead];
            unqueuedFrames += frames;
            queuedFrames -= frames;
            queueHead = (queueHead + 1) % queueIds.length;
            queueCount--;
        }

        long offset = 0;
        int currentState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (currentState == AL10.AL_PLAYING || currentState == AL10.AL_PAUSED) {
            offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
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
        if (clearErrors()) {
            fail();
        }
    }

    private void resetQueue() {
        queueCount = 0;
        queueHead = 0;
        nextBuffer = 0;
        pendingBytes = 0;
        queuedFrames = 0;
    }

    private void fail() {
        if (failed) {
            return;
        }
        failed = true;
        log("OpenAL output failed, audio is dropped until the next track");
    }

    private void drop(int length) {
        int frames = Math.max(0, length) / frameSize;
        if (frames <= 0) {
            return;
        }
        synchronized (lock) {
            unqueuedFrames += frames;
            if (unqueuedFrames > playedFrames) {
                playedFrames = unqueuedFrames;
            }
        }
        long millis = Math.round(frames * 1000.0 / sampleRate);
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            closed = true;
        }
    }

    private int state() {
        return source == 0 ? AL10.AL_STOPPED : AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
    }

    private boolean clearErrors() {
        boolean reported = false;
        for (int error = AL10.alGetError(); error != AL10.AL_NO_ERROR; error = AL10.alGetError()) {
            reported = true;
        }
        return reported;
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
