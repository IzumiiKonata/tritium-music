package tritium.music.client.rendering.font;

import tritium.music.client.config.FontConfig;
import tritium.music.core.assets.AssetCatalog;
import tritium.music.core.assets.AssetManager;
import tritium.music.core.assets.RemoteAsset;
import tritium.music.core.util.AsyncUtil;
import tritium.music.platform.Platform;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FontCatalog {

    private static final String FONT_PATH = "/assets/tritium-music/fonts";
    private static final String[] EXTENSIONS = {".ttf", ".otf"};
    private static final Set<String> ICON_FONTS = Set.of("music.ttf", "icomoon.ttf");

    private static final String[] FALLBACK_BUILTINS = {
            "pf_normal.ttf", "pf_middleblack.ttf", "sfregular.otf", "sfbold.otf"
    };

    private static final String SOURCE_KEY_BUILTIN = "tritium-music.ui.settings.font.source.builtin";
    private static final String SOURCE_KEY_SYSTEM = "tritium-music.ui.settings.font.source.system";
    private static final String SOURCE_KEY_USER = "tritium-music.ui.settings.font.source.user";

    private static final AtomicBoolean SYSTEM_LOADING = new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_LOADED = new AtomicBoolean();

    private static volatile List<FontOption> builtin = List.of();
    private static volatile List<FontOption> user = List.of();
    private static volatile List<FontOption> system = List.of();
    private static volatile List<FontOption> cached = List.of();

    private FontCatalog() {
    }

    public static void preload() {
        if (builtin.isEmpty()) {
            refresh();
        }
        preloadSystemFontsAsync();
    }

    public static void refresh() {
        builtin = scanBuiltins();
        user = scanUserFonts();
        cached = combine();
    }

    public static void preloadSystemFontsAsync() {
        if (!SYSTEM_LOADING.compareAndSet(false, true)) {
            return;
        }
        AsyncUtil.runAsync(() -> {
            try {
                loadSystemFonts();
            } finally {
                SYSTEM_LOADED.set(true);
                SYSTEM_LOADING.set(false);
            }
        });
    }

    public static boolean systemFontsReady() {
        return SYSTEM_LOADED.get();
    }

    public static List<FontOption> all() {
        if (cached.isEmpty()) {
            refresh();
        }
        return cached;
    }

    public static List<FontOption> search(String query) {
        List<FontOption> source = all();
        if (query == null || query.isBlank()) {
            return source;
        }
        List<FontOption> result = new ArrayList<>();
        for (FontOption option : source) {
            if (option.matches(query)) {
                result.add(option);
            }
        }
        return result;
    }

    public static FontOption find(String source, String value) {
        if (source == null || value == null) {
            return null;
        }
        for (FontOption option : all()) {
            if (option.source().equals(source) && option.value().equals(value)) {
                return option;
            }
        }
        return null;
    }

    public static String describe(String source, String value) {
        FontOption option = find(source, value);
        return option == null ? value : option.displayName();
    }

    public static File userFontDir() {
        File dir = new File(Platform.configDir(), "fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static void loadSystemFonts() {
        List<FontOption> options = new ArrayList<>();
        try {
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
            Set<String> seen = new LinkedHashSet<>();
            for (String family : families) {
                if (family == null || family.isBlank() || !seen.add(family)) {
                    continue;
                }
                options.add(new FontOption(FontConfig.SOURCE_SYSTEM, family, family, SOURCE_KEY_SYSTEM));
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        options.sort(Comparator.comparing(FontOption::displayName, String.CASE_INSENSITIVE_ORDER));
        system = options;
        cached = combine();
    }

    private static List<FontOption> combine() {
        List<FontOption> result = new ArrayList<>(builtin);
        result.addAll(user);
        result.addAll(system);
        return List.copyOf(result);
    }

    private static List<FontOption> scanBuiltins() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : FALLBACK_BUILTINS) {
            names.add(name);
        }
        names.addAll(scanBundledFonts());
        names.addAll(scanManagedFonts());

        List<FontOption> options = new ArrayList<>();
        for (String name : names) {
            options.add(new FontOption(FontConfig.SOURCE_BUILTIN, name, stripExtension(name), SOURCE_KEY_BUILTIN));
        }
        options.sort(Comparator.comparing(FontOption::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(options);
    }

    private static List<String> scanBundledFonts() {
        URL url = FontCatalog.class.getResource(FONT_PATH);
        if (url == null || !"file".equals(url.getProtocol())) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try {
            File[] files = new File(url.toURI()).listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (file.isFile() && hasFontExtension(name) && !ICON_FONTS.contains(name)) {
                        names.add(file.getName());
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return names;
    }

    private static List<String> scanManagedFonts() {
        List<String> names = new ArrayList<>();
        AssetManager manager = AssetManager.get();
        for (RemoteAsset asset : AssetCatalog.all()) {
            if (asset.essential() && manager.isReady(asset.path())) {
                names.add(asset.fileName());
            }
        }
        return names;
    }

    private static List<FontOption> scanUserFonts() {
        List<FontOption> options = new ArrayList<>();
        try {
            File dir = userFontDir();
            Path root = dir.toPath();
            try (var stream = Files.walk(root, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> hasFontExtension(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> options.add(new FontOption(
                                FontConfig.SOURCE_FILE,
                                path.toAbsolutePath().toString(),
                                stripExtension(path.getFileName().toString()),
                                SOURCE_KEY_USER)));
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        options.sort(Comparator.comparing(FontOption::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(options);
    }

    private static boolean hasFontExtension(String name) {
        for (String extension : EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index <= 0 ? name : name.substring(0, index);
    }
}
