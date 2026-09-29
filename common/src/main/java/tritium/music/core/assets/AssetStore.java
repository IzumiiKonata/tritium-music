package tritium.music.core.assets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AssetStore {

    private static final String STAMP_SUFFIX = ".sha256";
    private static final String DIRECTORY_PROPERTY = "tritium.assets.dir";
    private static final int[] GAME_DIR_PARENTS = {0, 1, 2, 3};

    private final File targetRoot;
    private final List<File> localRoots;

    private AssetStore(File targetRoot, List<File> localRoots) {
        this.targetRoot = targetRoot;
        this.localRoots = localRoots;
    }

    static AssetStore create(File targetRoot, File gameDir) {
        List<File> roots = new ArrayList<>();
        String override = System.getProperty(DIRECTORY_PROPERTY, "").trim();
        if (!override.isEmpty()) {
            roots.add(new File(override));
        }
        if (gameDir != null) {
            File current = gameDir.getAbsoluteFile();
            for (int depth : GAME_DIR_PARENTS) {
                for (int step = 0; step < depth && current != null; step++) {
                    current = current.getParentFile();
                }
                if (current != null && !roots.contains(current)) {
                    roots.add(current);
                }
                current = gameDir.getAbsoluteFile();
            }
        }
        return new AssetStore(targetRoot, List.copyOf(roots));
    }

    File target(RemoteAsset asset) {
        return new File(targetRoot, asset.path());
    }

    File local(RemoteAsset asset) {
        for (File root : localRoots) {
            File candidate = new File(root, asset.path());
            if (candidate.isFile() && candidate.length() == asset.size()) {
                return candidate;
            }
        }
        return null;
    }

    File file(RemoteAsset asset) {
        File local = local(asset);
        return local != null ? local : target(asset);
    }

    boolean isReady(RemoteAsset asset) {
        return local(asset) != null || isVerified(asset);
    }

    boolean isVerified(RemoteAsset asset) {
        File file = target(asset);
        if (!file.isFile() || file.length() != asset.size()) {
            return false;
        }
        String expected = asset.sha256();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        File stamp = stamp(asset);
        if (stamp.isFile()) {
            try {
                if (expected.equalsIgnoreCase(Files.readString(stamp.toPath(), StandardCharsets.UTF_8).trim())) {
                    return true;
                }
            } catch (IOException ignored) {
            }
        }
        if (!digest(file).equalsIgnoreCase(expected)) {
            return false;
        }
        writeStamp(asset);
        return true;
    }

    boolean install(RemoteAsset asset, Path part) throws IOException {
        File target = target(asset);
        if (!verify(asset, part.toFile())) {
            return false;
        }
        Files.createDirectories(target.toPath().getParent());
        Files.move(part, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        writeStamp(asset);
        return true;
    }

    void discard(RemoteAsset asset) {
        try {
            Files.deleteIfExists(target(asset).toPath());
            Files.deleteIfExists(stamp(asset).toPath());
        } catch (IOException ignored) {
        }
    }

    private boolean verify(RemoteAsset asset, File file) {
        if (!file.isFile() || file.length() != asset.size()) {
            return false;
        }
        String expected = asset.sha256();
        return expected == null || expected.isEmpty() || digest(file).equalsIgnoreCase(expected);
    }

    private void writeStamp(RemoteAsset asset) {
        String expected = asset.sha256();
        if (expected == null || expected.isEmpty()) {
            return;
        }
        try {
            Files.writeString(stamp(asset).toPath(), expected.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private File stamp(RemoteAsset asset) {
        return new File(targetRoot, asset.path() + STAMP_SUFFIX);
    }

    private static String digest(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            try (InputStream input = new DigestInputStream(Files.newInputStream(file.toPath()), digest)) {
                while (input.read(buffer) > 0) {
                }
            }
            StringBuilder builder = new StringBuilder(64);
            for (byte value : digest.digest()) {
                builder.append(Character.forDigit(value >> 4 & 0xf, 16));
                builder.append(Character.forDigit(value & 0xf, 16));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }
}
