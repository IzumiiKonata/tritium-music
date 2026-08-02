package tritium.music.core.lyric;

import com.google.gson.JsonObject;
import tritium.music.core.util.Pair;
import tritium.music.core.lyric.provider.LyricsResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricParser {
    private static final long QQ_QRC_ADVANCE_MILLIS = 150;
    private static final Pattern QQ_CREDIT_LINE = Pattern.compile(
            "(?i)^\\s*(?:歌曲(?:名称)?|歌名|曲名|歌手|演唱|主唱|作词|填词|词|作曲|谱曲|曲|编曲|制作人?|监制|混音|录音|母带|吉他|贝斯|鼓|和声|弦乐|音乐总监|发行|出品|制作|配唱|title|artist|singer|vocal|lyrics?|lyricist|composer|arranger|producer|mix(?:ing)?|master(?:ing)?|recording|guitar|bass|drums?|chorus|op|sp)(?:\\s*[:：/]|\\s+-\\s+|\\s+).+$");
    private static final Pattern QQ_TITLE_ARTIST_LINE = Pattern.compile("^.{1,80}\\s[-–—]\\s.{1,80}$");

    public static List<LyricLine> parse(LyricsResult result) {
        if (result == null || result.isEmpty()) return new ArrayList<>();
        if ("netease".equals(result.format())) {
            return parse(com.google.gson.JsonParser.parseString(result.lyrics()).getAsJsonObject());
        }
        if ("ttml".equals(result.format())) {
            return TtmlLyricParser.parse(result.lyrics());
        }
        if ("qrc".equals(result.format())) {
            List<LyricLine> lines = parseQrc(result.lyrics());
            removeQqCredits(lines);
            if (result.translation() != null && !result.translation().isBlank()) {
                applySecondaryLyrics(lines, parseQqSecondary(result.translation()), true, 100);
            }
            if (result.romanization() != null && !result.romanization().isBlank()) {
                applySecondaryLyrics(lines, parseQqSecondary(result.romanization()), false, 100);
            }
            advanceQqTimeline(lines);
            return lines;
        }
        if ("plain".equals(result.format())) {
            return List.of(new LyricLine(0, result.lyrics().trim()));
        }
        List<LyricLine> lines = new ArrayList<>(parseSingleLine(replace(result.lyrics())));
        if (result.translation() != null && !result.translation().isBlank()) {
            applySecondaryLyrics(lines, parseSingleLine(replace(result.translation())), true);
        }
        if (result.romanization() != null && !result.romanization().isBlank()) {
            applySecondaryLyrics(lines, parseSingleLine(replace(result.romanization())), false);
        }
        return lines;
    }

    private static List<LyricLine> parseQrc(String input) {
        List<LyricLine> lines = new ArrayList<>();
        Pattern linePattern = Pattern.compile("\\[(\\d+),(\\d+)](.*?)(?=\\[\\d+,\\d+]|$)", Pattern.DOTALL);
        Pattern wordPattern = Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
        Matcher lineMatcher = linePattern.matcher(input);
        while (lineMatcher.find()) {
            long timestamp = Long.parseLong(lineMatcher.group(1));
            long duration = Long.parseLong(lineMatcher.group(2));
            Matcher wordMatcher = wordPattern.matcher(lineMatcher.group(3));
            List<LyricLine.Word> words = new ArrayList<>();
            StringBuilder lyric = new StringBuilder();
            while (wordMatcher.find()) {
                String word = wordMatcher.group(1);
                long wordTimestamp = Long.parseLong(wordMatcher.group(2));
                long wordDuration = Long.parseLong(wordMatcher.group(3));
                if (word.isEmpty()) continue;
                lyric.append(word);
                words.add(new LyricLine.Word(word, wordTimestamp, wordDuration));
            }
            if (lyric.isEmpty()) continue;
            LyricLine line = new LyricLine(timestamp, lyric.toString());
            line.duration = duration;
            line.words.addAll(words);
            lines.add(line);
        }
        lines.sort(Comparator.comparingLong(LyricLine::getTimestamp));
        return lines;
    }

    private static void advanceQqTimeline(List<LyricLine> lines) {
        for (LyricLine line : lines) {
            line.timestamp = Math.max(0, line.timestamp - QQ_QRC_ADVANCE_MILLIS);
            List<LyricLine.Word> shiftedWords = new ArrayList<>(line.words.size());
            for (LyricLine.Word word : line.words) {
                shiftedWords.add(new LyricLine.Word(
                        word.word,
                        Math.max(0, word.timestamp - QQ_QRC_ADVANCE_MILLIS),
                        word.duration
                ));
            }
            line.words.clear();
            line.words.addAll(shiftedWords);
        }
    }

    private static void removeQqCredits(List<LyricLine> lines) {
        int removeCount = 0;
        for (LyricLine line : lines) {
            String text = line.lyric.strip();
            if (!QQ_CREDIT_LINE.matcher(text).matches() && !QQ_TITLE_ARTIST_LINE.matcher(text).matches()) break;
            removeCount++;
        }
        if (removeCount > 0) lines.subList(0, removeCount).clear();
    }

    private static List<LyricLine> parseQqSecondary(String input) {
        if (Pattern.compile("\\[\\d+,\\d+]").matcher(input).find()) return parseQrc(input);
        return parseSingleLine(replace(input));
    }

    public static List<LyricLine> parse(JsonObject input) {
        if (input.has("uncollected") || !input.has("lrc")) {
            return new ArrayList<>();
        }

        List<LyricLine> lyricLines = new ArrayList<>(parseSingleLine(replace(input.getAsJsonObject("lrc").get("lyric").getAsString())));

        processTranslationLyrics(input, lyricLines);
        processRomanizationLyrics(input, lyricLines);

        if (input.has("yrc")) {
            String yrc = replace(input.getAsJsonObject("yrc").get("lyric").getAsString());
            parseYrc(yrc, lyricLines);
            processTranslationLyricsYRC(input, lyricLines);
            processRomanizationLyricsYRC(input, lyricLines);
        }

        return lyricLines;
    }

    private static String replace(String input) {
        return input.replace(' ', ' ').replaceAll(" {2,}", " ");
    }

    private static void processTranslationLyricsYRC(JsonObject input, List<LyricLine> lyricLines) {
        if (!input.has("ytlrc")) return;

        String tLyric = input.getAsJsonObject("ytlrc").get("lyric").getAsString();
        if (tLyric.trim().isEmpty()) return;

        List<LyricLine> translates = parseSingleLine(tLyric);

        Map<Long, String> transMap = new HashMap<>();
        for (LyricLine t : translates) {
            transMap.put(t.timestamp, t.lyric);
        }

        for (LyricLine l : lyricLines) {
            String translation = transMap.get(l.timestamp);

            if (translation == null) {
                continue;
            }

            if (l.translationText == null) {
                l.translationText = translation;
            }
        }
    }

    private static void processRomanizationLyricsYRC(JsonObject input, List<LyricLine> lyricLines) {
        if (!input.has("yromalrc")) return;

        String romanization = input.getAsJsonObject("yromalrc").get("lyric").getAsString();
        if (romanization.isEmpty()) return;

        List<LyricLine> romanizations = parseSingleLine(romanization);

        Map<Long, String> romaMap = new HashMap<>();
        for (LyricLine r : romanizations) {
            romaMap.put(r.timestamp, r.lyric);
        }

        for (LyricLine l : lyricLines) {
            String roma = romaMap.get(l.timestamp);
            if (roma != null && l.romanizationText == null) {
                l.romanizationText = roma;
            }
        }
    }

    private static void processTranslationLyrics(JsonObject input, List<LyricLine> lyricLines) {
        if (!input.has("tlyric")) return;

        String tLyric = replace(input.getAsJsonObject("tlyric").get("lyric").getAsString());
        if (tLyric.trim().isEmpty()) return;

        List<LyricLine> translates = parseSingleLine(tLyric);

        Map<Long, String> transMap = new HashMap<>();
        for (LyricLine t : translates) {
            transMap.put(t.timestamp, t.lyric);
        }

        for (LyricLine l : lyricLines) {
            String translation = transMap.get(l.timestamp);
            if (translation != null && l.translationText == null) {
                l.translationText = translation;
            }
        }
    }

    private static void applySecondaryLyrics(List<LyricLine> lyricLines, List<LyricLine> secondary, boolean translation) {
        applySecondaryLyrics(lyricLines, secondary, translation, 0);
    }

    private static void applySecondaryLyrics(List<LyricLine> lyricLines, List<LyricLine> secondary, boolean translation, long tolerance) {
        Map<Long, String> values = new HashMap<>();
        for (LyricLine line : secondary) values.put(line.timestamp, line.lyric);
        for (LyricLine line : lyricLines) {
            String value = values.get(line.timestamp);
            if (value == null && tolerance > 0) {
                LyricLine closest = null;
                long distance = Long.MAX_VALUE;
                for (LyricLine candidate : secondary) {
                    long candidateDistance = Math.abs(candidate.timestamp - line.timestamp);
                    if (candidateDistance < distance) {
                        closest = candidate;
                        distance = candidateDistance;
                    }
                }
                if (closest != null && distance <= tolerance) value = closest.lyric;
            }
            if (value == null) continue;
            if (translation && line.translationText == null) line.translationText = value;
            if (!translation && line.romanizationText == null) line.romanizationText = value;
        }
    }

    private static void processRomanizationLyrics(JsonObject input, List<LyricLine> lyricLines) {
        if (!input.has("romalrc")) return;

        String romanization = replace(input.getAsJsonObject("romalrc").get("lyric").getAsString());
        if (romanization.isEmpty()) return;

        List<LyricLine> romanizations = parseSingleLine(romanization);

        Map<Long, String> romaMap = new HashMap<>();
        for (LyricLine r : romanizations) {
            romaMap.put(r.timestamp, r.lyric);
        }

        for (LyricLine l : lyricLines) {
            String roma = romaMap.get(l.timestamp);
            if (roma != null && l.romanizationText == null) {
                l.romanizationText = roma;
            }
        }
    }

    private static List<LyricLine> parseSingleLine(String input) {
        List<LyricLine> lyricLines = new ArrayList<>();
        String[] lines = input.split("\\n");
        if (lines.length == 1) lines = input.split("\\\\n");

        for (String line : lines) {
            List<LyricLine> parsedLines = parseLine(line);
            if (parsedLines != null) {
                lyricLines.addAll(parsedLines);
            }
        }

        lyricLines.sort(Comparator.comparingLong(LyricLine::getTimestamp));
        return lyricLines;
    }

    private static List<LyricLine> parseLine(String input) {
        if (input.isEmpty()) {
            return null;
        }
        boolean alt = false;
        input = input.trim();
        Matcher lineMatcher = Pattern.
                compile("((?:\\[\\d{2}:\\d{2}\\.\\d{2,3}])+)(.*)").matcher(input);
        if (!lineMatcher.matches()) {
            lineMatcher = Pattern.
                    compile("((?:\\[\\d{2}:\\d{2}:\\d{2,3}])+)(.*)").matcher(input);
            if (!lineMatcher.matches()) {
                return null;
            }
            alt = true;
        }
        String times = lineMatcher.group(1);
        String text = lineMatcher.group(2).trim();
        if (text.isEmpty()) {
            return null;
        }

        List<LyricLine> entryList = new ArrayList<>();
        Matcher timeMatcher = Pattern.compile(alt ? "\\[(\\d\\d):(\\d\\d):(\\d{2,3})]" : "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})]").matcher(times);
        while (timeMatcher.find()) {
            long min = Long.parseLong(timeMatcher.group(1));
            long sec = Long.parseLong(timeMatcher.group(2));
            String milStr = timeMatcher.group(3);

            long mil;
            if (milStr.length() == 3) {
                mil = Long.parseLong(milStr);
            } else {
                mil = Long.parseLong(milStr) * 10;
            }

            long time =
                    min * 60000 +
                            sec * 1000 +
                            mil;

            entryList.add(new LyricLine(time, text.replace("　", " ")));
        }
        return entryList;
    }

    public static void parseYrc(String yrc, List<LyricLine> lyricLines) {
        String[] lines = yrc.split("\n");
        if (lines.length == 1) lines = yrc.split("\\\\n");

        lyricLines.clear();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("[")) continue;

            String timeData = line.substring(1, line.indexOf("]"));
            String[] timeParts = timeData.split(",");
            long startDuration = Long.parseLong(timeParts[0]);
            long duration = Long.parseLong(timeParts[1]);

            if (i > 0 && startDuration == 0)
                continue;

            LyricLine l = new LyricLine(startDuration, "");
            l.duration = duration;

            parseWordTimings(l, line.substring(line.indexOf("]") + 1));

            StringBuilder sb = new StringBuilder();
            for (LyricLine.Word word : l.words) {
                sb.append(word.word);
            }

            l.lyric = sb.toString();

            lyricLines.add(l);
        }
    }

    private static void parseWordTimings(LyricLine l, String text) {
        Pattern pattern = Pattern.compile("\\((\\d+),(\\d+),0\\)((?!\\(\\d+,\\d+,0\\)|\\(\\d+,\\d+,0\\\\).)*");
        Matcher matcher = pattern.matcher(text);

        List<Pair<String, Pair<Long, Long>>> words = new ArrayList<>();

        while (matcher.find()) {
            String group = matcher.group();
            String metadata = group.substring(1, group.indexOf(")") + 1);
            String[] metadataParts = metadata.split(",");
            String lyric = group.substring(group.indexOf(")") + 1);

            long timestamp = Long.parseLong(metadataParts[0]);
            long duration = Long.parseLong(metadataParts[1]);

            if (duration <= 0 && !words.isEmpty()) {
                Pair<String, Pair<Long, Long>> last = words.getLast();
                words.set(words.size() - 1, Pair.of(last.getA() + lyric, last.getB()));
            } else {
                words.add(Pair.of(lyric, Pair.of(timestamp, duration)));
            }
        }

        words.stream().map(
                t -> new LyricLine.Word(
                        t.getA(),
                        t.getB().getA(),
                        t.getB().getB()
                )
        ).forEach(l.words::add);
    }
}
