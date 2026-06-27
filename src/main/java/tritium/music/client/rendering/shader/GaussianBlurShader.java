package tritium.music.client.rendering.shader;

import java.util.List;

/**
 * Backdrop gaussian blur. The multi-pass Blaze3D implementation is not yet wired
 * up (see {@link tritium.music.client.render.MusicRenderTarget}); for now the
 * queued render callbacks are executed directly so content still renders.
 */
public class GaussianBlurShader {

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
