package tritium.music.core.assets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AndroidNatives {

    private static final String ONNXRUNTIME_SKIP = "onnxruntime.native.onnxruntime.skip";
    private static final String ONNXRUNTIME_JNI_SKIP = "onnxruntime.native.onnxruntime4j_jni.skip";
    private static final String DIRECTORY_PROPERTY = "tritium.natives.dir";
    private static final String DIRECTORY = "tritium-music";
    private static final String NATIVES = "natives";
    private static final List<String> ROOT_PROPERTIES = List.of("java.io.tmpdir", "user.home", "user.dir");
    private static final List<String> ROOT_ENVIRONMENT = List.of("TMPDIR", "HOME");
    private static final List<String> NON_EXECUTABLE_ROOTS =
            List.of("/storage", "/sdcard", "/mnt/sdcard", "/mnt/media_rw", "/mnt/usb");

    private AndroidNatives() {
    }

    public static File stage(List<RemoteAsset> natives, File source) throws IOException {
        if (!AssetPlatform.isAndroid() || source == null || natives == null || natives.isEmpty()) {
            return source;
        }
        if (!isNonExecutable(source)) {
            return source;
        }

        File target = directory(source.getName());
        for (RemoteAsset asset : natives) {
            File from = new File(source, asset.fileName());
            if (!from.isFile()) {
                throw new IOException("missing native " + from.getAbsolutePath());
            }
            copy(from.toPath(), target.toPath().resolve(asset.fileName()));
        }
        return target;
    }

    public static Path executableDirectory(String name) throws IOException {
        if (!AssetPlatform.isAndroid()) {
            return Files.createTempDirectory("tritium-" + name + "-");
        }
        return directory(name + "-" + UUID.randomUUID()).toPath();
    }

    public static String preload(List<RemoteAsset> natives, File... directories) {
        if (!AssetPlatform.isAndroid() || natives == null || natives.isEmpty()) {
            return null;
        }

        System.setProperty(ONNXRUNTIME_SKIP, Boolean.TRUE.toString());
        System.setProperty(ONNXRUNTIME_JNI_SKIP, Boolean.TRUE.toString());

        String failure = null;
        for (File directory : directories) {
            if (directory == null) {
                continue;
            }
            try {
                for (RemoteAsset asset : natives) {
                    System.load(new File(directory, asset.fileName()).getAbsolutePath());
                }
                return null;
            } catch (Throwable throwable) {
                failure = throwable.toString();
            }
        }
        return failure;
    }

    private static File directory(String name) throws IOException {
        File target = new File(new File(stagingRoot(), NATIVES), name);
        Files.createDirectories(target.toPath());
        target.setExecutable(true, true);
        return target;
    }

    private static void copy(Path from, Path to) throws IOException {
        if (Files.isRegularFile(to) && Files.size(to) == Files.size(from)) {
            return;
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        to.toFile().setReadable(true, true);
        to.toFile().setExecutable(true, true);
    }

    private static File stagingRoot() {
        String override = System.getProperty(DIRECTORY_PROPERTY, "").trim();
        if (!override.isEmpty()) {
            return new File(override);
        }

        for (String property : ROOT_PROPERTIES) {
            File root = candidate(System.getProperty(property));
            if (root != null) {
                return root;
            }
        }
        for (String variable : ROOT_ENVIRONMENT) {
            File root = candidate(System.getenv(variable));
            if (root != null) {
                return root;
            }
        }
        return new File(System.getProperty("java.io.tmpdir", "."), DIRECTORY);
    }

    private static File candidate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        File root = new File(value.trim(), DIRECTORY);
        if (isNonExecutable(root)) {
            return null;
        }
        try {
            Files.createDirectories(root.toPath());
        } catch (IOException exception) {
            return null;
        }
        return root;
    }

    private static boolean isNonExecutable(File file) {
        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);
        for (String prefix : NON_EXECUTABLE_ROOTS) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
