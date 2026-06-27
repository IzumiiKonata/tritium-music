package tritium.music.client.screens.ncm;

import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.animation.spring.SpringAnimation;
import tritium.music.client.rendering.animation.spring.SpringParams;
import tritium.music.core.lyric.LyricLine;

/**
 * Client-side lyric layout/measurement. Kept out of the engine-agnostic core so
 * {@link LyricLine} carries only numeric render state, not font/animation deps.
 */
public final class LyricLayout {

    private LyricLayout() {
    }

    public static SpringAnimation spring(LyricLine line) {
        if (line.spring instanceof SpringAnimation s) {
            return s;
        }
        SpringAnimation s = new SpringAnimation(new SpringParams(.9, 14, 90, false));
        line.spring = s;
        return s;
    }

    public static void computeHeight(LyricLine line, double width) {
        CFontRenderer fr = FontManager.pf65bold;

        if (!line.words.isEmpty()) {
            double height = fr.getHeight();
            double w = 0;
            for (LyricLine.Word word : line.words) {
                double wordWidth = fr.getStringWidthD(word.word);
                if (w + wordWidth > width) {
                    w = wordWidth;
                    height += fr.getHeight() * .85 + 4;
                } else {
                    w += wordWidth;
                }
            }
            line.height = height;
        } else {
            int length = fr.fitWidth(line.lyric, width).length;
            line.height = length * fr.getHeight() * .85 + length * 4;
        }

        if (line.translationText != null) {
            CFontRenderer frTranslation = FontManager.pf34bold;
            String[] strings = frTranslation.fitWidth(line.translationText, width);
            line.height += frTranslation.getHeight() * strings.length + 4 * (strings.length - 1) + 8;
        }
    }
}
