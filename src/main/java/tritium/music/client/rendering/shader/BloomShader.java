package tritium.music.client.rendering.shader;

import java.util.List;

/**
 * Glow/bloom effect. The Blaze3D implementation is not yet wired up; the queued
 * render callbacks are executed directly so content still renders without glow.
 */
public class BloomShader {

    public void run(List<Runnable> runnables) {
        for (Runnable runnable : runnables) {
            runnable.run();
        }
    }

    public void runNoCaching(List<Runnable> runnables) {
        for (Runnable runnable : runnables) {
            runnable.run();
        }
    }
}
