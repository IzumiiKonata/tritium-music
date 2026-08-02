package tritium.music.client.screens.ncm;

import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.animation.spring.SpringAnimation;
import tritium.music.client.rendering.animation.spring.SpringParams;
import tritium.music.core.lyric.LyricLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side lyric layout/measurement. Kept out of the engine-agnostic core so
 * {@link LyricLine} carries only numeric render state, not font/animation deps.
 */
public final class LyricLayout {

    private static final Map<LyricLine, WordLayout> WORD_LAYOUT_CACHE = new WeakHashMap<>();

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
            int lineCount = wordLayout(line, width).lineCount();
            line.height = fr.getHeight() + Math.max(0, lineCount - 1) * (fr.getHeight() * .85 + 4);
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

    public static List<WordFragment> wrapWords(LyricLine line, double width) {
        return wordLayout(line, width).fragments();
    }

    private static WordLayout wordLayout(LyricLine line, double width) {
        CFontRenderer font = FontManager.pf65bold;
        long widthBits = Double.doubleToLongBits(width);
        long layoutGeneration = font.getLayoutGeneration();
        int boundaryHash = 1;
        for (LyricLine.Word word : line.words) {
            boundaryHash = 31 * boundaryHash + word.word.length();
        }

        synchronized (WORD_LAYOUT_CACHE) {
            WordLayout cached = WORD_LAYOUT_CACHE.get(line);
            if (cached != null && cached.widthBits() == widthBits && cached.boundaryHash() == boundaryHash
                    && cached.layoutGeneration() == layoutGeneration && cached.lyric().equals(line.lyric)) {
                return cached;
            }
        }

        List<WordFragment> fragments = new ArrayList<>();
        List<CFontRenderer.WrappedLine> wrappedLines = font.fitWidthLines(line.lyric, width);

        int wordStart = 0;
        int wordIndex = 0;
        for (int lineIndex = 0; lineIndex < wrappedLines.size(); lineIndex++) {
            CFontRenderer.WrappedLine wrappedLine = wrappedLines.get(lineIndex);
            while (wordIndex < line.words.size()) {
                LyricLine.Word word = line.words.get(wordIndex);
                int wordEnd = wordStart + word.word.length();
                if (wordEnd > wrappedLine.startIndex()) {
                    break;
                }
                wordStart = wordEnd;
                wordIndex++;
            }

            int currentWordStart = wordStart;
            int currentWordIndex = wordIndex;
            while (currentWordIndex < line.words.size() && currentWordStart < wrappedLine.endIndex()) {
                LyricLine.Word word = line.words.get(currentWordIndex);
                int currentWordEnd = currentWordStart + word.word.length();
                int fragmentStart = Math.max(wrappedLine.startIndex(), currentWordStart);
                int fragmentEnd = Math.min(wrappedLine.endIndex(), currentWordEnd);
                if (fragmentStart < fragmentEnd) {
                    int startInWord = fragmentStart - currentWordStart;
                    fragments.add(new WordFragment(word,
                            word.word.substring(startInWord, fragmentEnd - currentWordStart),
                            startInWord, lineIndex));
                }
                currentWordStart = currentWordEnd;
                currentWordIndex++;
            }
        }

        WordLayout result = new WordLayout(line.lyric, widthBits, layoutGeneration, boundaryHash,
                wrappedLines.size(), List.copyOf(fragments));
        if (font.isLayoutStable(line.lyric)) {
            synchronized (WORD_LAYOUT_CACHE) {
                WORD_LAYOUT_CACHE.put(line, result);
            }
        }
        return result;
    }

    public record WordFragment(LyricLine.Word word, String text, int startInWord, int lineIndex) {
    }

    private record WordLayout(String lyric, long widthBits, long layoutGeneration, int boundaryHash, int lineCount,
                              List<WordFragment> fragments) {
    }
}
