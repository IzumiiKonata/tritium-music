package tritium.music.core.assets;

import java.util.Locale;
import java.util.Set;

public final class AssetPlatform {

    public static final String WINDOWS_X64 = "win-x64";
    public static final String LINUX_X64 = "linux-x64";
    public static final String LINUX_AARCH64 = "linux-aarch64";
    public static final String MACOS_AARCH64 = "osx-aarch64";

    private static final Set<String> SUPPORTED = Set.of(WINDOWS_X64, LINUX_X64, LINUX_AARCH64, MACOS_AARCH64);

    private static final String CURRENT = detect();

    private AssetPlatform() {
    }

    public static String current() {
        return CURRENT;
    }

    public static boolean isSupported() {
        return CURRENT != null;
    }

    public static String osName() {
        return System.getProperty("os.name", "");
    }

    public static String osArch() {
        return System.getProperty("os.arch", "");
    }

    private static String detect() {
        String os = osName().toLowerCase(Locale.ROOT);
        String arch = osArch().toLowerCase(Locale.ROOT);

        String osId;
        if (os.contains("win")) {
            osId = "win";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osId = "osx";
        } else if (os.contains("linux")) {
            osId = "linux";
        } else {
            return null;
        }

        String archId = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> null;
        };
        if (archId == null) {
            return null;
        }

        String id = osId + "-" + archId;
        return SUPPORTED.contains(id) ? id : null;
    }
}
