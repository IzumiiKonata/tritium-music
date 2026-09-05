package tritium.music.core.audio;

import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AutoMixRules {
    public static final String FILE_NAME = "automix-rules.json";
    private static final Object LOCK = new Object();
    private static Path loadedPath;
    private static long loadedModified = Long.MIN_VALUE;
    private static long loadedSize = Long.MIN_VALUE;
    private static final List<Rule> loadedRules = new CopyOnWriteArrayList<>();
    private static volatile boolean rulesLoaded = false;

    private AutoMixRules() {
    }

    static {
        addPrebuiltRules();
    }

    static void addPrebuiltRules() {

        // Baptized In Fear -> Open Hearts
        Rule rule1 = new Rule();

        rule1.outgoingSongId = 2670863319L;
        rule1.incomingSongId = 2670863152L;

        rule1.outgoingStartMillis = 232170;
        rule1.incomingStartMillis = 4;

        rule1.durationMillis = 5186;
        rule1.style = Style.GAPLESS;
        rule1.playbackRate = 1.0;
        rule1.pitchShiftSemitones = 0;
        rule1.tempoRampMillis = 4400;
        rule1.eqStrength = 0.8025;

        loadedRules.add(rule1);

    }

    public static Rule match(long outgoingSongId, long incomingSongId) {
        return match(rules(), outgoingSongId, incomingSongId);
    }

    static Rule match(List<Rule> rules, long outgoingSongId, long incomingSongId) {
        for (Rule rule : rules) {
            if (rule.enabled && rule.outgoingSongId == outgoingSongId && rule.incomingSongId == incomingSongId) {
                return rule;
            }
        }
        return null;
    }

    static List<Rule> parse(String json) {
        RuleFile file = JsonUtils.parse(json, RuleFile.class);
        if (file == null || file.rules == null) {
            return List.of();
        }
        List<Rule> valid = new ArrayList<>();
        for (Rule rule : file.rules) {
            if (rule != null && rule.isValid()) {
                valid.add(rule);
            }
        }
        return List.copyOf(valid);
    }

    private static List<Rule> rules() {
        Path path = new File(Platform.configDir(), FILE_NAME).toPath();
        synchronized (LOCK) {

            if (rulesLoaded)
                return loadedRules;

            try {
                ensureFile(path);
                long modified = Files.getLastModifiedTime(path).toMillis();
                long size = Files.size(path);
                if (path.equals(loadedPath) && modified == loadedModified && size == loadedSize) {
                    return loadedRules;
                }
                loadedPath = path;
                loadedModified = modified;
                loadedSize = size;
                List<Rule> parsed = parse(Files.readString(path, StandardCharsets.UTF_8));
                loadedRules.addAll(parsed);
                Platform.log("[NCM] Loaded " + parsed.size() + " hard AutoMix rule(s) from " + FILE_NAME);
            } catch (Exception e) {
                Platform.log("[NCM] Failed to load " + FILE_NAME + ": " + e.getMessage());
            }

            rulesLoaded = true;

            return loadedRules;
        }
    }

    private static void ensureFile(Path path) throws Exception {
        if (Files.exists(path)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, JsonUtils.toJsonString(new RuleFile()) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public enum Style {
        GAPLESS,
        CROSSFADE,
        NATURAL_FADE,
        SILENCE_SKIP,
        MUSICAL_BLEND
    }

    public static final class Rule {
        public boolean enabled = true;
        public long outgoingSongId;
        public long incomingSongId;
        public long outgoingStartMillis;
        public long incomingStartMillis;
        public long durationMillis = 6_000;
        public Style style = Style.CROSSFADE;
        public double playbackRate = 1;
        public int pitchShiftSemitones;
        public long tempoRampMillis;
        public double eqStrength = 0.55;

        public boolean isValid() {
            return outgoingSongId > 0
                    && incomingSongId > 0
                    && outgoingStartMillis >= 0
                    && incomingStartMillis >= 0
                    && durationMillis >= 900
                    && style != null
                    && Double.isFinite(playbackRate)
                    && playbackRate >= 0.5
                    && playbackRate <= 2
                    && pitchShiftSemitones >= -12
                    && pitchShiftSemitones <= 12
                    && tempoRampMillis >= 0
                    && Double.isFinite(eqStrength)
                    && eqStrength >= 0
                    && eqStrength <= 1;
        }
    }

    private static final class RuleFile {
        private List<Rule> rules = new ArrayList<>();
    }
}
