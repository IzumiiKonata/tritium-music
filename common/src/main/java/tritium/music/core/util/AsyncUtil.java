package tritium.music.core.util;

import lombok.experimental.UtilityClass;
import tritium.music.platform.Platform;

@UtilityClass
public class AsyncUtil {

    public void runAsync(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        Platform.runAsync(runnable);
    }

    public void runOnRenderThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        Platform.runOnRenderThread(runnable);
    }
}
