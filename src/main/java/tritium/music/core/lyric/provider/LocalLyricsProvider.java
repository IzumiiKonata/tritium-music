package tritium.music.core.lyric.provider;

import tritium.music.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class LocalLyricsProvider implements LyricsProvider {
    private final Path root;

    public LocalLyricsProvider() {
        this(Platform.configDir().toPath().resolve("lyrics"));
    }

    public LocalLyricsProvider(Path root) {
        this.root = root;
    }

    @Override
    public Optional<LyricsResult> search(LyricsQuery query) {
        try {
            Files.createDirectories(root);
            List<String> patterns = new ArrayList<>();
            if (!query.artists().isBlank() && !query.title().isBlank() && !query.album().isBlank()) {
                patterns.add(query.artists() + " - " + query.title() + " - " + query.album());
            }
            if (!query.artists().isBlank() && !query.title().isBlank()) {
                patterns.add(query.artists() + " - " + query.title());
            }
            if (!query.title().isBlank()) {
                patterns.add(query.title());
            }
            List<String> normalizedPatterns = patterns.stream().map(LocalLyricsProvider::normalize).toList();
            try (Stream<Path> paths = Files.walk(root)) {
                Optional<Path> match = paths.filter(Files::isRegularFile)
                        .filter(LocalLyricsProvider::supported)
                        .filter(path -> normalizedPatterns.stream().anyMatch(pattern -> normalize(stem(path)).contains(pattern)))
                        .findFirst();
                if (match.isEmpty()) {
                    return Optional.empty();
                }
                Path path = match.get();
                String lyrics = Files.readString(path, StandardCharsets.UTF_8);
                return Optional.of(new LyricsResult(lyrics, format(path, lyrics), "local"));
            }
        } catch (IOException e) {
            LyricsFetcher.log("Local lookup failed for id=" + query.songId() + ", reason=" + e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttml") || name.endsWith(".xml") || name.endsWith(".lrc") || name.endsWith(".txt");
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String normalize(String value) {
        return value.codePoints()
                .filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))
                .map(Character::toLowerCase)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String format(Path path, String lyrics) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ttml") || name.endsWith(".xml") || lyrics.contains("<tt")) {
            return "ttml";
        }
        if (name.endsWith(".lrc") || lyrics.matches("(?s).*\\[\\d{1,3}:\\d{2}[.:]\\d{1,3}].*")) {
            return "lrc";
        }
        return "plain";
    }
}
