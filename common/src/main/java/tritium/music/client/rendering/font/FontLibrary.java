package tritium.music.client.rendering.font;

import tritium.music.client.config.FontConfig;
import tritium.music.core.assets.AssetCatalog;
import tritium.music.core.assets.AssetManager;
import tritium.music.platform.Platform;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FontLibrary {

    public static final String FONT_PATH = "/assets/tritium-music/fonts/";

    private static final long MAX_FONT_BYTES = 64L * 1024L * 1024L;

    public record Key(String source, String value, String style) {
    }

    private static final Object LOCK = new Object();
    private static final Map<Key, Entry> CACHE = new HashMap<>();

    private FontLibrary() {
    }

    public static Loaded acquire(String source, String value, String style) {
        Key key = new Key(source, value, style);
        synchronized (LOCK) {
            Entry entry = CACHE.get(key);
            if (entry == null) {
                entry = create(key);
                CACHE.put(key, entry);
            }
            entry.refs++;
            return new Loaded(key, entry);
        }
    }

    public static boolean canLoad(String source, String value, String style) {
        Entry entry;
        synchronized (LOCK) {
            entry = create(new Key(source, value, style));
        }
        entry.dispose();
        return entry.available();
    }

    public static void disposeAll() {
        synchronized (LOCK) {
            for (Entry entry : CACHE.values()) {
                entry.dispose();
            }
            CACHE.clear();
        }
    }

    public static int invalidateWithoutShaper() {
        synchronized (LOCK) {
            List<Key> doomed = new ArrayList<>();
            for (Map.Entry<Key, Entry> cached : CACHE.entrySet()) {
                if (cached.getValue().shaper == null) {
                    doomed.add(cached.getKey());
                }
            }
            for (Key key : doomed) {
                Entry entry = CACHE.remove(key);
                if (entry != null) {
                    entry.dispose();
                }
            }
            return doomed.size();
        }
    }

    public static int invalidateUnavailable() {
        synchronized (LOCK) {
            List<Key> doomed = new ArrayList<>();
            for (Map.Entry<Key, Entry> cached : CACHE.entrySet()) {
                if (!cached.getValue().available()) {
                    doomed.add(cached.getKey());
                }
            }
            for (Key key : doomed) {
                Entry entry = CACHE.remove(key);
                if (entry != null) {
                    entry.dispose();
                }
            }
            return doomed.size();
        }
    }

    private static void release(Key key, Entry entry) {
        synchronized (LOCK) {
            if (entry.refs > 0) {
                entry.refs--;
            }
            if (entry.refs == 0 && CACHE.get(key) == entry) {
                CACHE.remove(key);
                entry.dispose();
            }
        }
    }

    private static Entry create(Key key) {
        try {
            return createUnsafe(key);
        } catch (Throwable throwable) {
            Platform.log("[font] unable to load font " + key.source() + ":" + key.value()
                    + " (" + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()) + ")");
            return new Entry(null, null);
        }
    }

    private static Entry createUnsafe(Key key) {
        int style = FontConfig.styleValue(key.style());
        if (style < 0) {
            style = Font.PLAIN;
        }

        if (FontConfig.SOURCE_SYSTEM.equals(key.source())) {
            return createSystem(key.value(), style);
        }

        byte[] data = readBytes(key);
        if (data == null) {
            return new Entry(null, null);
        }
        return createFromBytes(data, style, key.value());
    }

    private static Entry createSystem(String family, int style) {
        File file = SystemFontIndex.fileFor(family, style);
        if (file != null) {
            byte[] data = readFile(file);
            if (data != null) {
                Entry entry = createFromBytes(data, style, family);
                if (entry.available()) {
                    return entry;
                }
            }
        }

        try {
            Font font = new Font(family, style, 1);
            if (font.getFamily().equalsIgnoreCase(family) || font.getName().equalsIgnoreCase(family)) {
                return new Entry(font, null);
            }
        } catch (Throwable throwable) {
            Platform.log("[font] system font lookup failed for " + family + ": " + throwable.getClass().getSimpleName());
        }
        return new Entry(null, null);
    }

    private static Entry createFromBytes(byte[] data, int style, String description) {
        Font font = createFont(data);
        if (font == null) {
            return new Entry(null, null);
        }

        font = font.deriveFont(style);

        FontShaper shaper = FontShaper.create(font, data, postScriptName(font), description);
        if (shaper != null && (!shaper.isUsable() || !shaper.matchesGlyphIds(font))) {
            shaper.dispose();
            shaper = null;
        }

        return new Entry(font, shaper);
    }

    public static Font createFont(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static boolean isPlausibleFontFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                return false;
            }
            long length = file.length();
            if (length < 12L || length > MAX_FONT_BYTES) {
                return false;
            }
            byte[] header = new byte[4];
            try (InputStream input = Files.newInputStream(file.toPath())) {
                if (input.read(header) != header.length) {
                    return false;
                }
            }
            int tag = (header[0] & 0xFF) << 24 | (header[1] & 0xFF) << 16 | (header[2] & 0xFF) << 8 | (header[3] & 0xFF);
            return tag == 0x00010000 || tag == 0x4F54544F || tag == 0x74727565
                    || tag == 0x74797031 || tag == 0x74746366;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static String postScriptName(Font font) {
        try {
            return font.getPSName();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static byte[] readBytes(Key key) {
        if (FontConfig.SOURCE_BUILTIN.equals(key.source())) {
            byte[] managed = readManaged(key.value());
            if (managed != null) {
                return managed;
            }
            try (InputStream stream = FontLibrary.class.getResourceAsStream(FONT_PATH + key.value())) {
                return stream == null ? null : stream.readAllBytes();
            } catch (Throwable throwable) {
                Platform.log("[font] bundled font " + key.value() + " could not be read: "
                        + throwable.getClass().getSimpleName());
                return null;
            }
        }
        if (FontConfig.SOURCE_FILE.equals(key.source())) {
            return readFile(new File(key.value()));
        }
        return null;
    }

    private static byte[] readManaged(String fileName) {
        String path = AssetCatalog.relativePathOf(fileName);
        if (path == null) {
            return null;
        }
        AssetManager manager = AssetManager.get();
        if (!manager.isReady(path)) {
            return null;
        }
        File file = manager.file(path);
        return file == null ? null : readFile(file);
    }

    private static byte[] readFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                return null;
            }
            long length = file.length();
            if (length <= 0 || length > MAX_FONT_BYTES) {
                return null;
            }
            return Files.readAllBytes(Path.of(file.getAbsolutePath()));
        } catch (Throwable throwable) {
            Platform.log("[font] " + file + " could not be read: " + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private static final class Entry {
        private final Font font;
        private final FontShaper shaper;
        private int refs;

        private Entry(Font font, FontShaper shaper) {
            this.font = font;
            this.shaper = shaper;
        }

        private void dispose() {
            if (shaper != null) {
                shaper.dispose();
            }
        }

        private boolean available() {
            return font != null;
        }
    }

    public static final class Loaded implements AutoCloseable {

        private final Key key;
        private final Entry entry;
        private boolean closed;

        private Loaded(Key key, Entry entry) {
            this.key = key;
            this.entry = entry;
        }

        public Font font() {
            return entry.font;
        }

        public FontShaper shaper() {
            return closed ? null : entry.shaper;
        }

        public boolean available() {
            return !closed && entry.available();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            release(key, entry);
        }
    }
}
