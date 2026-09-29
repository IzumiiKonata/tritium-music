package tritium.music.core.assets;

public final class AssetFormat {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private AssetFormat() {
    }

    public static String bytes(long bytes) {
        double value = Math.max(0, bytes);
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) {
            return (long) value + " " + UNITS[unit];
        }
        return String.format(java.util.Locale.ROOT, value < 10 ? "%.2f %s" : "%.1f %s", value, UNITS[unit]);
    }

    public static String speed(double bytesPerSecond) {
        return bytes((long) Math.max(0, bytesPerSecond)) + "/s";
    }

    public static String duration(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "--";
        }
        long total = Math.round(seconds);
        long hours = total / 3600;
        long minutes = total % 3600 / 60;
        long secs = total % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, secs);
    }
}
