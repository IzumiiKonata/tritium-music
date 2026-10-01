package tritium.music.platform;

import javax.sound.sampled.AudioFormat;

public interface PcmOutput extends AutoCloseable {

    AudioFormat format();

    int write(byte[] data, int offset, int length);

    long framePosition();

    void start();

    void stop();

    void flush();

    void drain();

    @Override
    void close();
}
