package tritium.music.client.rendering.shader;

import java.util.List;

public class GaussianBlurShader {

    public boolean isAvailable() {
        return true;
    }

    public void run(List<Runnable> runnables) {
        EffectQueue.captureBlur(runnables);
    }

    public void runNoCaching(List<Runnable> runnables) {
        EffectQueue.captureBlur(runnables);
    }
}
