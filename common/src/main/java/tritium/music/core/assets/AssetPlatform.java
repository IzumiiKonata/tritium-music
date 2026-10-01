package tritium.music.core.assets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AssetPlatform {

    public static final String WINDOWS_X64 = "win-x64";
    public static final String LINUX_X64 = "linux-x64";
    public static final String LINUX_AARCH64 = "linux-aarch64";
    public static final String MACOS_AARCH64 = "osx-aarch64";
    public static final String ANDROID_AARCH64 = "android-aarch64";

    private static final Set<String> SUPPORTED =
            Set.of(WINDOWS_X64, LINUX_X64, LINUX_AARCH64, MACOS_AARCH64, ANDROID_AARCH64);

    private static final List<String> ANDROID_MARKERS =
            List.of("/system/bin/linker64", "/system/bin/linker", "/system/build.prop");

    private static final List<String> GLIBC_MARKERS = List.of(
            "/lib/ld-linux-aarch64.so.1", "/lib/ld-linux-x86-64.so.2", "/lib64/ld-linux-x86-64.so.2",
            "/lib/ld-linux-armhf.so.3", "/lib/arm-linux-gnueabihf/ld-linux-armhf.so.3");

    private static final List<String> ANDROID_HOME_PREFIXES = List.of("/data/data/", "/data/user/");

    private static final boolean ANDROID = detectAndroid();

    private static final String CURRENT = detect();

    private AssetPlatform() {
    }

    public static String current() {
        return CURRENT;
    }

    public static boolean isSupported() {
        return CURRENT != null;
    }

    public static boolean isAndroid() {
        return ANDROID;
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
            osId = ANDROID ? "android" : "linux";
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

    private static boolean detectAndroid() {
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);

        if (vm.contains("dalvik") || runtime.contains("android") || vendor.contains("android")) {
            return true;
        }
        if (!osName().toLowerCase(Locale.ROOT).contains("linux")) {
            return false;
        }
        if (existsAny(GLIBC_MARKERS)) {
            return false;
        }
        if (existsAny(ANDROID_MARKERS)) {
            return true;
        }

        String home = System.getProperty("java.home", "");
        for (String prefix : ANDROID_HOME_PREFIXES) {
            if (home.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean existsAny(List<String> markers) {
        for (String marker : markers) {
            if (Files.exists(Path.of(marker))) {
                return true;
            }
        }
        return false;
    }
}
