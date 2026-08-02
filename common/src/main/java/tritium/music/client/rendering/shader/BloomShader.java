package tritium.music.client.rendering.shader;

import java.util.List;

public class BloomShader {

    public void run(List<Runnable> runnables) {
        EffectQueue.captureBloom(runnables);
    }

    public void runNoCaching(List<Runnable> runnables) {
        EffectQueue.captureBloom(runnables);
    }
}
