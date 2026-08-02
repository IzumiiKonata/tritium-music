package tritium.music.core.audio;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

final class PrefetchInputStream extends InputStream {
    private static final int READ_CHUNK_SIZE = 64 * 1024;

    private final InputStream source;
    private final byte[] buffer;
    private final Object lock = new Object();
    private final Thread worker;

    private int readPosition;
    private int writePosition;
    private int buffered;
    private boolean endOfStream;
    private boolean closed;
    private IOException failure;

    PrefetchInputStream(InputStream source, int capacity) {
        this.source = Objects.requireNonNull(source);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        buffer = new byte[capacity];
        worker = new Thread(this::prefetch, "Music Stream Prefetch");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int read = read(single, 0, 1);
        return read < 0 ? -1 : single[0] & 0xff;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (length == 0) {
            return 0;
        }
        synchronized (lock) {
            while (buffered == 0 && !endOfStream && failure == null && !closed) {
                waitForData();
            }
            if (buffered == 0) {
                if (failure != null) {
                    throw failure;
                }
                return -1;
            }
            int count = Math.min(length, buffered);
            int first = Math.min(count, buffer.length - readPosition);
            System.arraycopy(buffer, readPosition, target, offset, first);
            int second = count - first;
            if (second > 0) {
                System.arraycopy(buffer, 0, target, offset + first, second);
            }
            readPosition = (readPosition + count) % buffer.length;
            buffered -= count;
            lock.notifyAll();
            return count;
        }
    }

    @Override
    public int available() {
        synchronized (lock) {
            return buffered;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            lock.notifyAll();
        }
        worker.interrupt();
        source.close();
    }

    private void prefetch() {
        byte[] chunk = new byte[Math.min(READ_CHUNK_SIZE, buffer.length)];
        try {
            int read;
            while (!isClosed() && (read = source.read(chunk)) >= 0) {
                if (read > 0) {
                    append(chunk, read);
                }
            }
        } catch (IOException e) {
            synchronized (lock) {
                if (!closed) {
                    failure = e;
                }
            }
        } finally {
            synchronized (lock) {
                endOfStream = true;
                lock.notifyAll();
            }
            try {
                source.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void append(byte[] sourceBuffer, int length) {
        int offset = 0;
        while (offset < length) {
            synchronized (lock) {
                while (buffered == buffer.length && !closed) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (closed) {
                    return;
                }
                int count = Math.min(length - offset, buffer.length - buffered);
                int first = Math.min(count, buffer.length - writePosition);
                System.arraycopy(sourceBuffer, offset, buffer, writePosition, first);
                int second = count - first;
                if (second > 0) {
                    System.arraycopy(sourceBuffer, offset + first, buffer, 0, second);
                }
                writePosition = (writePosition + count) % buffer.length;
                buffered += count;
                offset += count;
                lock.notifyAll();
            }
        }
    }

    private void waitForData() throws IOException {
        try {
            lock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for prefetched audio", e);
        }
    }

    private boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }
}
