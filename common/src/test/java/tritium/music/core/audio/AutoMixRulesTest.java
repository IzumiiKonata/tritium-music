package tritium.music.core.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMixRulesTest {
    @Test
    void parsesAnExactDirectedTransitionRule() {
        List<AutoMixRules.Rule> rules = AutoMixRules.parse("""
                {
                  "rules": [
                    {
                      "outgoingSongId": 111,
                      "incomingSongId": 222,
                      "outgoingStartMillis": 173500,
                      "incomingStartMillis": 32000,
                      "durationMillis": 8500,
                      "style": "MUSICAL_BLEND",
                      "playbackRate": 1.02,
                      "pitchShiftSemitones": -1,
                      "tempoRampMillis": 2500,
                      "eqStrength": 0.8
                    }
                  ]
                }
                """);

        assertEquals(1, rules.size());
        AutoMixRules.Rule rule = rules.getFirst();
        assertEquals(111, rule.outgoingSongId);
        assertEquals(222, rule.incomingSongId);
        assertEquals(AutoMixRules.Style.MUSICAL_BLEND, rule.style);
        assertEquals(8_500, rule.durationMillis);
        assertEquals(1.02, rule.playbackRate);
    }

    @Test
    void keepsDefaultsForOptionalTransitionFields() {
        List<AutoMixRules.Rule> rules = AutoMixRules.parse("""
                {
                  "rules": [
                    {
                      "outgoingSongId": 111,
                      "incomingSongId": 222,
                      "outgoingStartMillis": 10000,
                      "incomingStartMillis": 500
                    }
                  ]
                }
                """);

        AutoMixRules.Rule rule = rules.getFirst();
        assertEquals(6_000, rule.durationMillis);
        assertEquals(AutoMixRules.Style.CROSSFADE, rule.style);
        assertEquals(1, rule.playbackRate);
        assertEquals(0.55, rule.eqStrength);
    }

    @Test
    void rejectsUnsafeOrIncompleteRules() {
        List<AutoMixRules.Rule> rules = AutoMixRules.parse("""
                {
                  "rules": [
                    {"outgoingSongId": 0, "incomingSongId": 222, "durationMillis": 6000},
                    {"outgoingSongId": 111, "incomingSongId": 222, "durationMillis": 200},
                    {"outgoingSongId": 111, "incomingSongId": 222, "durationMillis": 6000, "eqStrength": 2}
                  ]
                }
                """);

        assertTrue(rules.isEmpty());
    }

    @Test
    void matchesOnlyTheConfiguredDirectionAndSkipsDisabledRules() {
        List<AutoMixRules.Rule> rules = AutoMixRules.parse("""
                {
                  "rules": [
                    {
                      "enabled": false,
                      "outgoingSongId": 111,
                      "incomingSongId": 222,
                      "outgoingStartMillis": 9000
                    },
                    {
                      "outgoingSongId": 111,
                      "incomingSongId": 222,
                      "outgoingStartMillis": 10000
                    }
                  ]
                }
                """);

        assertSame(rules.get(1), AutoMixRules.match(rules, 111, 222));
        assertNull(AutoMixRules.match(rules, 222, 111));
    }

    @Test
    void acceptsGaplessRulesWithoutTransitionTuning() {
        List<AutoMixRules.Rule> rules = AutoMixRules.parse("""
                {
                  "rules": [
                    {
                      "outgoingSongId": 2670863319,
                      "incomingSongId": 2670863152,
                      "outgoingStartMillis": 232170,
                      "incomingStartMillis": 4,
                      "style": "GAPLESS"
                    }
                  ]
                }
                """);

        assertEquals(1, rules.size());
        assertEquals(AutoMixRules.Style.GAPLESS, rules.getFirst().style);
    }
}
