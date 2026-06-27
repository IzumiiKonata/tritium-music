package tritium.music.fabric.ui;

public final class Ease {

    private Ease() {
    }

    public static float outQuart(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u;
    }

    public static float inOutQuad(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public static float outCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Frame-rate independent approach toward a target.
     *
     * @param speed fraction per second to close the remaining distance (0..1+)
     */
    public static float approach(float current, float target, float speed, float deltaSeconds) {
        float factor = clamp01(speed * deltaSeconds);
        return current + (target - current) * factor;
    }
}
