package tritium.music.client.rendering.font;

import tritium.music.client.config.FontConfig;

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
            throwable.printStackTrace();
        }
        return new Entry(null, null);
    }

    private static Entry createFromBytes(byte[] data, int style, String description) {
        Font font = null;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        if (font == null) {
            return new Entry(null, null);
        }

        font = font.deriveFont(style);

        FontShaper shaper = null;
        try {
            shaper = new FontShaper(data, font.getPSName(), description);
            if (!shaper.isUsable() || !shaper.matchesGlyphIds(font)) {
                shaper.dispose();
                shaper = null;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return new Entry(font, shaper);
    }

    private static byte[] readBytes(Key key) {
        if (FontConfig.SOURCE_BUILTIN.equals(key.source())) {
            try (InputStream stream = FontLibrary.class.getResourceAsStream(FONT_PATH + key.value())) {
                return stream == null ? null : stream.readAllBytes();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                return null;
            }
        }
        if (FontConfig.SOURCE_FILE.equals(key.source())) {
            return readFile(new File(key.value()));
        }
        return null;
    }

    private static byte[] readFile(File file) {
        try {
            if (!file.isFile()) {
                return null;
            }
            return Files.readAllBytes(Path.of(file.getAbsolutePath()));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
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
