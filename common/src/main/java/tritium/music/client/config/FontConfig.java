package tritium.music.client.config;

import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class FontConfig {

    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_SYSTEM = "system";
    public static final String SOURCE_FILE = "file";

    public static final String STYLE_PLAIN = "PLAIN";
    public static final String STYLE_BOLD = "BOLD";
    public static final String STYLE_ITALIC = "ITALIC";
    public static final String STYLE_BOLD_ITALIC = "BOLD_ITALIC";

    public static final String DEFAULT_MAIN = "pf_normal.ttf";
    public static final String DEFAULT_ENGLISH = "sfregular.otf";
    public static final String DEFAULT_MAIN_BOLD = "pf_middleblack.ttf";
    public static final String DEFAULT_ENGLISH_BOLD = "sfbold.otf";

    private static volatile FontConfig instance;

    public Slot main = Slot.builtin(DEFAULT_MAIN);
    public Slot english = Slot.builtin(DEFAULT_ENGLISH);
    public Slot mainBold = Slot.builtin(DEFAULT_MAIN_BOLD);
    public Slot englishBold = Slot.builtin(DEFAULT_ENGLISH_BOLD);

    public boolean shaping = true;
    public boolean discretionaryLigatures = false;

    public enum SlotType {
        MAIN,
        ENGLISH,
        MAIN_BOLD,
        ENGLISH_BOLD
    }

    public static class Slot {

        public String source = SOURCE_BUILTIN;
        public String value = "";
        public String style = STYLE_PLAIN;

        public Slot() {
        }

        public Slot(String source, String value, String style) {
            this.source = source;
            this.value = value;
            this.style = style;
        }

        public static Slot builtin(String value) {
            return new Slot(SOURCE_BUILTIN, value, STYLE_PLAIN);
        }

        public Slot copy() {
            return new Slot(source, value, style);
        }

        public int awtStyle() {
            return styleValue(style);
        }

        public void normalize(String fallbackValue) {
            if (source == null || source.isBlank()) {
                source = SOURCE_BUILTIN;
            }
            if (!SOURCE_BUILTIN.equals(source) && !SOURCE_SYSTEM.equals(source) && !SOURCE_FILE.equals(source)) {
                source = SOURCE_BUILTIN;
            }
            if (value == null || value.isBlank()) {
                value = fallbackValue;
                source = SOURCE_BUILTIN;
            }
            if (styleValue(style) < 0) {
                style = STYLE_PLAIN;
            }
        }
    }

    public static FontConfig get() {
        FontConfig local = instance;
        if (local == null) {
            synchronized (FontConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    public Slot slot(SlotType type) {
        return switch (type) {
            case MAIN -> main;
            case ENGLISH -> english;
            case MAIN_BOLD -> mainBold;
            case ENGLISH_BOLD -> englishBold;
        };
    }

    public void set(SlotType type, Slot slot) {
        switch (type) {
            case MAIN -> main = slot;
            case ENGLISH -> english = slot;
            case MAIN_BOLD -> mainBold = slot;
            case ENGLISH_BOLD -> englishBold = slot;
        }
    }

    public static String defaultValue(SlotType type) {
        return switch (type) {
            case MAIN -> DEFAULT_MAIN;
            case ENGLISH -> DEFAULT_ENGLISH;
            case MAIN_BOLD -> DEFAULT_MAIN_BOLD;
            case ENGLISH_BOLD -> DEFAULT_ENGLISH_BOLD;
        };
    }

    public static int styleValue(String style) {
        if (style == null) {
            return -1;
        }
        return switch (style) {
            case STYLE_PLAIN -> Font.PLAIN;
            case STYLE_BOLD -> Font.BOLD;
            case STYLE_ITALIC -> Font.ITALIC;
            case STYLE_BOLD_ITALIC -> Font.BOLD | Font.ITALIC;
            default -> -1;
        };
    }

    public FontConfig copy() {
        FontConfig copy = new FontConfig();
        copy.main = main == null ? Slot.builtin(DEFAULT_MAIN) : main.copy();
        copy.english = english == null ? Slot.builtin(DEFAULT_ENGLISH) : english.copy();
        copy.mainBold = mainBold == null ? Slot.builtin(DEFAULT_MAIN_BOLD) : mainBold.copy();
        copy.englishBold = englishBold == null ? Slot.builtin(DEFAULT_ENGLISH_BOLD) : englishBold.copy();
        copy.shaping = shaping;
        copy.discretionaryLigatures = discretionaryLigatures;
        return copy;
    }

    public void assign(FontConfig other) {
        main = other.main.copy();
        english = other.english.copy();
        mainBold = other.mainBold.copy();
        englishBold = other.englishBold.copy();
        shaping = other.shaping;
        discretionaryLigatures = other.discretionaryLigatures;
    }

    public void reset() {
        main = Slot.builtin(DEFAULT_MAIN);
        english = Slot.builtin(DEFAULT_ENGLISH);
        mainBold = Slot.builtin(DEFAULT_MAIN_BOLD);
        englishBold = Slot.builtin(DEFAULT_ENGLISH_BOLD);
        shaping = true;
        discretionaryLigatures = false;
    }

    public boolean isDefault(SlotType type) {
        Slot slot = slot(type);
        return slot != null
                && SOURCE_BUILTIN.equals(slot.source)
                && defaultValue(type).equals(slot.value)
                && STYLE_PLAIN.equals(slot.style);
    }

    private static File file() {
        return new File(Platform.configDir(), "fonts.json");
    }

    public static FontConfig load() {
        File f = file();
        FontConfig config = null;

        if (f.exists()) {
            try {
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                config = JsonUtils.parse(json, FontConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (config == null) {
            config = new FontConfig();
        }

        config.normalize();
        return config;
    }

    public void save() {
        normalize();
        try {
            Files.writeString(file().toPath(), JsonUtils.toJsonString(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void normalize() {
        if (main == null) main = Slot.builtin(DEFAULT_MAIN);
        if (english == null) english = Slot.builtin(DEFAULT_ENGLISH);
        if (mainBold == null) mainBold = Slot.builtin(DEFAULT_MAIN_BOLD);
        if (englishBold == null) englishBold = Slot.builtin(DEFAULT_ENGLISH_BOLD);

        main.normalize(DEFAULT_MAIN);
        english.normalize(DEFAULT_ENGLISH);
        mainBold.normalize(DEFAULT_MAIN_BOLD);
        englishBold.normalize(DEFAULT_ENGLISH_BOLD);

        shaping = true;
        discretionaryLigatures = false;
    }
}
