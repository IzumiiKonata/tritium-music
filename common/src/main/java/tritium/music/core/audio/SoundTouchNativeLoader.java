package tritium.music.core.audio;

import com.tianscar.soundtouch.SoundTouch;
import tritium.music.core.assets.AndroidNatives;
import tritium.music.core.assets.AssetPlatform;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class SoundTouchNativeLoader {
    private static final String UTILITY_CLASS = "com.tianscar.soundtouch.Util";

    private static boolean loaded;

    private SoundTouchNativeLoader() {
    }

    static synchronized void load() throws Exception {
        if (loaded) {
            return;
        }
        Class<?> utility = Class.forName(UTILITY_CLASS, true, SoundTouch.class.getClassLoader());
        try {
            loadWithLibraryLoader(utility);
        } catch (Throwable libraryFailure) {
            try {
                loadExtractedNatives(utility);
            } catch (Throwable extractionFailure) {
                IOException failure = new IOException(extractionFailure + " (library loader: " + libraryFailure + ")", extractionFailure);
                failure.addSuppressed(libraryFailure);
                throw failure;
            }
        }
        loaded = true;
    }

    private static void loadWithLibraryLoader(Class<?> utility) throws Exception {
        Method loadLibrary = utility.getDeclaredMethod("loadLibrary");
        loadLibrary.setAccessible(true);
        try {
            loadLibrary.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private static void loadExtractedNatives(Class<?> utility) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String directory;
        String library;
        String binding;
        if (AssetPlatform.isAndroid()) {
            if (!arch.contains("aarch64") && !arch.contains("arm64")) {
                throw new IOException("Unsupported SoundTouch platform " + os + " " + arch);
            }
            directory = "android-arm64-v8a";
            library = "libSoundTouchDLL.so";
            binding = "libsoundtouchjni.so";
        } else if (os.contains("win")) {
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
        Path extraction = AndroidNatives.executableDirectory("soundtouch");
        Path libraryPath = extract(directory, library, extraction);
        Path bindingPath = extract(directory, binding, extraction);
        System.load(libraryPath.toAbsolutePath().toString());
        System.load(bindingPath.toAbsolutePath().toString());
        markLibrariesLoadedBestEffort(utility);
        libraryPath.toFile().deleteOnExit();
        bindingPath.toFile().deleteOnExit();
        extraction.toFile().deleteOnExit();
    }

    private static void markLibrariesLoadedBestEffort(Class<?> utility) {
        try {
            Field librariesLoaded = utility.getDeclaredField("librariesLoaded");
            librariesLoaded.setAccessible(true);
            Object state = librariesLoaded.get(null);
            if (state instanceof AtomicBoolean atomic) {
                atomic.set(true);
            } else {
                librariesLoaded.setBoolean(null, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Path extract(String directory, String name, Path destination) throws IOException {
        String resource = "/" + directory + "/" + name;
        try (InputStream input = SoundTouchNativeLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing SoundTouch native " + resource);
            }
            Path output = destination.resolve(name);
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        }
    }
}
