package tritium.music.client.rendering.font;

import tritium.music.core.util.AsyncUtil;

import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class SystemFontIndex {

    private static final String[] EXTENSIONS = {".ttf", ".otf", ".ttc", ".otc"};
    private static final int MAX_DEPTH = 4;

    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final Map<String, File> FILES = new ConcurrentHashMap<>();

    private SystemFontIndex() {
    }

    public static boolean isReady() {
        return LOADED.get();
    }

    public static void preload() {
        preload(null);
    }

    public static void preload(Runnable onReady) {
        if (!LOADING.compareAndSet(false, true)) {
            return;
        }
        AsyncUtil.runAsync(() -> {
            try {
                scan();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            } finally {
                LOADED.set(true);
                if (onReady != null) {
                    AsyncUtil.runOnRenderThread(onReady);
                }
            }
        });
    }

    public static File fileFor(String family, int style) {
        if (family == null || FILES.isEmpty()) {
            return null;
        }
        String key = family.toLowerCase(Locale.ROOT);
        File exact = FILES.get(key + "|" + (style & (Font.BOLD | Font.ITALIC)));
        if (exact != null) {
            return exact;
        }
        return FILES.get(key + "|" + Font.PLAIN);
    }

    private static void scan() {
        List<File> directories = fontDirectories();
        for (File directory : directories) {
            if (!directory.isDirectory()) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(directory.toPath(), MAX_DEPTH)) {
                stream.filter(Files::isRegularFile)
                        .filter(SystemFontIndex::isFontFile)
                        .forEach(SystemFontIndex::index);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private static void index(Path path) {
        File file = path.toFile();
        try {
            Font[] fonts = Font.createFonts(file);
            for (Font font : fonts) {
                if (font == null || font.getFamily() == null) {
                    continue;
                }
                String key = font.getFamily().toLowerCase(Locale.ROOT)
                        + "|" + (font.getStyle() & (Font.BOLD | Font.ITALIC));
                FILES.putIfAbsent(key, file);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isFontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static List<File> fontDirectories() {
        List<File> directories = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");

        if (os.contains("win")) {
            String windir = System.getenv("WINDIR");
            if (windir != null) {
                directories.add(new File(windir, "Fonts"));
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                directories.add(new File(localAppData, "Microsoft/Windows/Fonts"));
            }
        } else if (os.contains("mac")) {
            directories.add(new File("/System/Library/Fonts"));
            directories.add(new File("/Library/Fonts"));
            directories.add(new File(home, "Library/Fonts"));
        } else {
            directories.add(new File("/usr/share/fonts"));
            directories.add(new File("/usr/local/share/fonts"));
            directories.add(new File(home, ".fonts"));
            directories.add(new File(home, ".local/share/fonts"));
        }

        return directories;
    }
}
