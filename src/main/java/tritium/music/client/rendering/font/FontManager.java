package tritium.music.client.rendering.font;

import lombok.SneakyThrows;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FontManager {

    public static CFontRenderer pf12bold, pf14bold, pf16bold, pf18bold, pf20bold, pf25bold, pf28bold, pf34bold, pf40bold, pf50bold, pf65bold;
    public static CFontRenderer pf12, pf14, pf16, pf18, pf20, pf25, pf32;
    public static CFontRenderer icon30;
    public static CFontRenderer music18, music40;

    private static final String FONT_PATH = "/assets/tritium-music/fonts/";

    private static final Map<String, Font> fonts = new HashMap<>();
    private static final Map<String, FontKerning> fontKernings = new HashMap<>();

    private static volatile boolean loaded = false;

    public static void loadFonts() {
        if (loaded) {
            return;
        }
        loaded = true;

        String normalName = "pf_normal";
        String boldName = "pf_middleblack";

        pf12 = create(12, normalName);
        pf14 = create(14, normalName);
        pf16 = create(16, normalName);
        pf18 = create(18, normalName);
        pf20 = create(20, normalName);
        pf25 = create(25, normalName);
        pf32 = create(32, normalName);

        pf12bold = create(12, boldName);
        pf14bold = create(14, boldName);
        pf16bold = create(16, boldName);
        pf18bold = create(18, boldName);
        pf20bold = create(20, boldName);
        pf25bold = create(25, boldName);
        pf28bold = create(28, boldName);
        pf34bold = create(34, boldName);
        pf40bold = create(40, boldName);
        pf50bold = create(50, boldName);
        pf65bold = create(65, boldName);

        icon30 = create(30, "icomoon");

        music18 = create(18, "music");
        music40 = create(40, "music");
    }

    private static Font readFont(String path) {
        return fonts.computeIfAbsent(path, p -> {
            try (InputStream resourceAsStream = FontManager.class.getResourceAsStream(p)) {
                if (resourceAsStream == null) {
                    throw new RuntimeException("Font resource not found: " + p);
                }
                return Font.createFont(Font.TRUETYPE_FONT, resourceAsStream);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private static FontKerning readFontKerning(String path) {
        return fontKernings.computeIfAbsent(path, p -> {
            try {
                return new FontKerning(p);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    @SneakyThrows
    public static CFontRenderer create(float size, String name) {
        Font font = readFont(FONT_PATH + name + ".ttf");
        FontKerning kerning = readFontKerning(FONT_PATH + name + ".ttf");

        return switch (name) {
            case "pf_normal" -> {
                Font main = readFont(FONT_PATH + "sfregular.otf");
                FontKerning mainKerning = readFontKerning(FONT_PATH + "sfregular.otf");
                yield new CFontRenderer(main, size * 0.5f, mainKerning, font);
            }
            case "pf_middleblack" -> {
                Font main = readFont(FONT_PATH + "sfbold.otf");
                FontKerning mainKerning = readFontKerning(FONT_PATH + "sfbold.otf");
                yield new CFontRenderer(main, size * 0.5f, mainKerning, font);
            }
            default -> new CFontRenderer(font, size * 0.5f, kerning, font);
        };
    }
}
