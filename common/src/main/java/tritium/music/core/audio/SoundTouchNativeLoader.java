package tritium.music.core.audio;

import com.tianscar.soundtouch.SoundTouch;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class SoundTouchNativeLoader {
    private static boolean loaded;

    private SoundTouchNativeLoader() {
    }

    static synchronized void load() throws Exception {
        if (loaded) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String directory;
        String library;
        String binding;
        if (os.contains("win")) {
            directory = arch.contains("aarch64") || arch.contains("arm64") ? "windows-aarch64" : arch.contains("64") ? "windows-x86_64" : "windows-x86";
            library = "SoundTouchDLL.dll";
            binding = "soundtouchjni.dll";
        } else if (os.contains("mac") || os.contains("osx")) {
            directory = arch.contains("aarch64") || arch.contains("arm64") ? "macos-arm64" : "macos-x86_64";
            library = "libSoundTouchDLL.dylib";
            binding = "libsoundtouchjni.dylib";
        } else if (os.contains("nux") || os.contains("nix")) {
            directory = arch.contains("64") ? "linux-amd64" : "linux-i386";
            library = "libSoundTouchDLL.so";
            binding = "libsoundtouchjni.so";
        } else {
            throw new IOException("Unsupported SoundTouch platform " + os + " " + arch);
        }
        Path extraction = Files.createTempDirectory("tritium-soundtouch-");
        Path libraryPath = extract(directory, library, extraction);
        Path bindingPath = extract(directory, binding, extraction);
        System.load(libraryPath.toAbsolutePath().toString());
        System.load(bindingPath.toAbsolutePath().toString());
        Class<?> utility = Class.forName("com.tianscar.soundtouch.Util", true, SoundTouchNativeLoader.class.getClassLoader());
        Field librariesLoaded = utility.getDeclaredField("librariesLoaded");
        librariesLoaded.setAccessible(true);
        Object loadedState = librariesLoaded.get(null);
        if (loadedState instanceof AtomicBoolean atomic) {
            atomic.set(true);
        } else {
            librariesLoaded.setBoolean(null, true);
        }
        libraryPath.toFile().deleteOnExit();
        bindingPath.toFile().deleteOnExit();
        extraction.toFile().deleteOnExit();
        loaded = true;
    }

    private static Path extract(String directory, String name, Path destination) throws IOException {
        String resource = "/" + directory + "/" + name;
        try (InputStream input = SoundTouch.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing SoundTouch native " + resource);
            }
            Path output = destination.resolve(name);
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        }
    }
}
