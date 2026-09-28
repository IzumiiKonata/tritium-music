package tritium.music;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tritium.music.client.rendering.font.FontShaper;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FontMetricsProbeTest {

    private static final String FONT_PATH = "/assets/tritium-music/fonts/";

    private static final String[] BUNDLED = {
            "pf_normal.ttf", "pf_middleblack.ttf", "sfregular.otf", "sfbold.otf"
    };

    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);

    @Test
    void bundledFontsKeepSaneLineMetrics() throws Exception {
        for (String name : BUNDLED) {
            byte[] data = read(name);
            assertNotNull(data, "missing bundled font " + name);

            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
            for (float size : new float[]{16f, 20f, 65f}) {
                LineMetrics metrics = font.deriveFont(size).getLineMetrics("Ag", CONTEXT);
                double ratio = (metrics.getAscent() + metrics.getDescent()) / size;
                assertTrue(ratio > 0.8 && ratio <= 1.401,
                        name + " @" + size + " band/em=" + ratio + " outside the sane range");
            }
        }
    }

    @Test
    void chineseFontsCoverCjkAndLatinFontsDoNot() throws Exception {
        assertTrue(load("pf_normal.ttf").canDisplay('中'));
        assertTrue(load("pf_middleblack.ttf").canDisplay('中'));
        assertFalse(load("sfregular.otf").canDisplay('中'));
        assertFalse(load("sfbold.otf").canDisplay('中'));
    }

    @Test
    void harfBuzzGlyphIdsMatchAwtGlyphCodes() throws Exception {
        Assumptions.assumeTrue(hasHarfBuzzNatives(), "LWJGL natives are not available in this JVM");

        for (String name : BUNDLED) {
            byte[] data = read(name);
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));

            FontShaper shaper = new FontShaper(data, font.getPSName(), name);
            try {
                assertTrue(shaper.isUsable(), "shaper unusable for " + name);
                assertTrue(shaper.matchesGlyphIds(font.deriveFont(20f)),
                        "HarfBuzz glyph ids diverge from AWT glyph codes for " + name);
            } finally {
                shaper.dispose();
            }
        }
    }

    private static boolean hasHarfBuzzNatives() {
        try {
            org.lwjgl.system.MemoryStack.stackPush().close();
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Font load(String name) throws Exception {
        return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(read(name)));
    }

    private static byte[] read(String name) throws Exception {
        try (InputStream stream = FontMetricsProbeTest.class.getResourceAsStream(FONT_PATH + name)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }
}
